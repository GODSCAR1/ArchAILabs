# ADR 0007: buscador y concurrencia del catálogo

- **Estado:** aceptado
- **Fecha:** 2026-10-31
- **Contexto:** `add-product-search`, `add-product-editing`, `add-stock-reservation`

## 1. Listado del buscador

### Contexto

El catálogo necesita combinar filtros, ordenar y paginar productos sin cargar asociaciones de forma
innecesaria ni convertir la consulta de `count` en otra consulta con fetches.

### Alternativas consideradas

- **A.** `Specification` por filtro, combinadas con `Specification.allOf`, fetches condicionales en
  la entidad para la consulta de página y conversión a DTO.
- **B.** Entity View de Blaze-Persistence para proyectar el resultado y controlar las asociaciones.
  No se midió porque requiere añadir una dependencia nueva.

### Decisión

Se adopta la alternativa A: `Specification` + entidad + DTO. Los fetches de categoría y stock solo
se aplican a la consulta de la página; la consulta de `count` no incluye fetches. No se añade
Blaze-Persistence.

### Evidencia

La medición se realizó con PostgreSQL/Testcontainers y 120 productos, más que el tamaño de batch de
50, después de limpiar las estadísticas de Hibernate. `ProductSearchQueryCountTest` pasa con el
umbral `<= 2` sentencias por página (consulta de página con fetches y consulta de `count`).

También quedaron documentados dos experimentos rojos en [docs/lab-s09.md](../lab-s09.md):

- Sin fetches, la conversión falló con `LazyInitializationException`.
- Sin desempate por id, la paginación devolvió 52 productos de los 60 esperados, con ids repetidos.

### Consecuencias

- El test de número de consultas debe conservar más de 50 filas y el umbral `<= 2`.
- Los fetches deben distinguir la consulta de página de la consulta de `count`.
- Si la consulta supera el umbral con el volumen objetivo, se debe revisar este ADR antes de cambiar
  de estrategia.

## 2. Edición con `@Version`

### Contexto

La edición es una interacción humana: puede transcurrir tiempo entre la lectura y el envío de los
cambios. Los conflictos son raros y no conviene retener locks mientras el usuario edita.

### Alternativas consideradas

- **A.** Optimistic locking con `@Version`.
- **B.** Bloqueo pesimista durante la edición, manteniendo la fila bloqueada mientras el usuario
  prepara o confirma los cambios.

### Decisión

Se adopta `@Version`. El `409` tiene dos caminos:

1. La aplicación compara explícitamente la versión enviada por el cliente con la versión leída y
   rechaza una versión ya obsoleta.
2. Aunque la comparación explícita pase, el `UPDATE` condicionado por `@Version` puede detectar un
   conflicto concurrente al guardar y traducirlo al `409` correspondiente.

Dentro de la transacción de edición, la entidad se desconecta tras leerla. Al guardar, el mapper
construye una entidad con la versión esperada y `saveAndFlush` hace un `merge`: Hibernate compara
esa versión con la de la base de datos y el `UPDATE` queda condicionado por ella. Si se modificara
el `@Version` de una entidad gestionada, Hibernate compararía contra la versión que él mismo cargó y
el conflicto no se detectaría. El coste es un `SELECT` adicional en el `merge`. Entre peticiones no
se retiene ningún lock: la edición humana transcurre fuera de cualquier transacción.

### Evidencia

- `ProductEditingIT` ejecutó dos `PUT` con la misma versión: el primero devolvió `200` y el segundo
  `409` con el problema de modificación concurrente.
- `ProductEditConflictTest` verifica el conflicto detectado al guardar y que los valores del segundo
  cambio no se persisten.
- El experimento rojo de `ProductEditConflictTest`, al enviar temporalmente `version + 1`, está
  documentado en [docs/lab-s09.md](../lab-s09.md): el test no produjo el conflicto esperado.

### Consecuencias

- Las ediciones humanas no mantienen locks de base de datos abiertos.
- El cliente debe conservar y reenviar la versión recibida.
- Un conflicto puede detectarse antes del guardado o mediante el `UPDATE` condicionado por la
  versión; ambos caminos mantienen el contrato `409`.

## 3. Reserva directa

### Contexto

La reserva directa debe comprobar disponibilidad, devolver el balance restante y exponer una espera
acotada cuando otra transacción retiene el stock. Ordering reserva varias líneas y no necesita
devolver el disponible.

### Alternativas consideradas

- **A.** Caso de uso transaccional con bloqueo pesimista.
- **B.** `UPDATE` condicional PostgreSQL con `RETURNING available`, seguido de un `SELECT` cuando no
  afecte filas para distinguir 404 de 409. Esta alternativa también devolvería el disponible, así
  que ese no es el motivo de la decisión.

### Decisión

Se conserva la reserva pesimista porque la regla de negocio vive en el dominio, en
`StockBalance.reserve`, y no en el `WHERE` de una consulta. El contrato expone una espera acotada
con `503` y `Retry-After`. El coste medido es asumible para este endpoint. Ordering mantiene
`discountIfAvailable` porque reserva varias líneas y no necesita el disponible.

### Evidencia

La comparación se realizó con PostgreSQL/Testcontainers, 50 reservas concurrentes de una unidad
contra 30 unidades, una ronda de calentamiento y cinco rondas medidas por ruta. Ambas rutas
produjeron 30 reservas, 20 insuficiencias y stock final 0 en cada ronda.

| Ruta | Mediana | Mínimo | Máximo | Líneas relevantes |
|---|---:|---:|---:|---:|
| Reserva pesimista (`ReserveProductStockUseCase`) | 118 ms | 108 ms | 121 ms | 20 |
| UPDATE atómico (`InventoryFacade` → `discountIfAvailable`) | 37 ms | 36 ms | 40 ms | 14 |

Las líneas relevantes incluyen consulta, adaptador y caso de uso, con anotaciones y firmas de esos
fragmentos. El detalle de la carga está en [docs/lab-s09.md](../lab-s09.md).

El timeout de 2 s se verificó con el hint JPA en Hibernate 7.4 + PostgreSQL 17: PostgreSQL devolvió
`55P03` (`lock_not_available`), Spring lo expuso como `CannotAcquireLockException` y el adaptador lo
tradujo a `StockBusyException`. La reserva falló en aproximadamente 2,0 s (el test exige entre 1,5
y 3,5 s). No fue necesario aplicar el plan B (`SET LOCAL lock_timeout`).

### Consecuencias

- La regla de reserva permanece testeable en el dominio y no se duplica en SQL.
- La API devuelve `503` con `Retry-After` cuando no puede adquirir el lock dentro del límite.
- La ruta atómica de ordering conserva su menor coste para reservas de varias líneas.

### Cuándo revisar esta decisión

Revisar esta decisión si la latencia de la reserva directa se vuelve crítica, o si, pasado un
tiempo, la reserva sigue sin más reglas que comparar unidades con el disponible: en ese caso la
ventaja de tener la regla en el dominio no compensa el coste, y el `UPDATE` condicional con
`RETURNING` sería la mejor opción.
