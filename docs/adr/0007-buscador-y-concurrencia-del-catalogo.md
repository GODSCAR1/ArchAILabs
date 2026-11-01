# ADR 0007: buscador y concurrencia del catálogo

- **Estado:** aceptado
- **Contexto:** `add-product-search`

## Decisión

Se adopta la alternativa A: `Specification` por filtro, combinadas con
`Specification.allOf`, y fetches de categoría y stock solo en la consulta de
la página. La consulta de count no incluye fetches. No se añade Blaze-Persistence
ni una dependencia nueva.

## Comparación

La medición se realiza con PostgreSQL/Testcontainers y 120 productos, más que el
tamaño de batch de 50, después de limpiar las estadísticas de Hibernate.

| Alternativa | Consulta de página | Consulta de count | Resultado | Entidades de stock por fila |
|---|---:|---:|---:|---:|
| A. Specification + fetch condicional | 1 | 1 | 2 observado, umbral `<= 2` | 0 adicionales |
| B. Entity View de Blaze-Persistence | no medida (requiere dependencia nueva) | no medida (requiere dependencia nueva) | no medida (requiere dependencia nueva) | no medida (requiere dependencia nueva) |

La alternativa B no se ha medido porque requiere una dependencia nueva que no
forma parte del proyecto y cuya adición está fuera del alcance autorizado. La
alternativa A cumple el umbral medido,
mantiene la consulta parametrizada y usa las capacidades ya instaladas.

## Consecuencias

- El test de número de consultas debe conservar más de 50 filas y el umbral `<= 2`.
- Los fetches deben distinguir la consulta de página de la consulta de count.
- Si la consulta supera el umbral con el volumen objetivo, se deberá revisar este ADR antes de cambiar de estrategia.

## Decisión de concurrencia de reservas

Para reservas directas se consideraron dos alternativas: el caso de uso transaccional con bloqueo
pesimista y un `UPDATE` condicional PostgreSQL con `RETURNING available`, seguido de un `SELECT`
cuando no afectase filas para distinguir 404 de 409. La segunda alternativa también devolvería el
disponible, por lo que ese no es el motivo de la decisión.

Se conserva el bloqueo pesimista porque la regla de negocio vive en el dominio, en
`StockBalance.reserve`, y no en el `WHERE` de una consulta. Además, el contrato expone una espera
acotada con `503` y `Retry-After`, y el coste medido, 118 ms de mediana frente a 37 ms para 50
reservas, es asumible para este endpoint. Ordering sigue con `discountIfAvailable` porque reserva
varias líneas y no necesita devolver el disponible.

La comparación se realizó con PostgreSQL/Testcontainers, 50 reservas concurrentes de una unidad
contra 30 unidades, una ronda de calentamiento y cinco rondas medidas por ruta. En cada ronda ambas
rutas produjeron 30 reservas, 20 insuficiencias y stock final 0.

| Ruta | Mediana | Mínimo | Máximo | Líneas relevantes |
|---|---:|---:|---:|---:|
| Reserva pesimista (`ReserveProductStockUseCase`) | 118 ms | 108 ms | 121 ms | 20 |
| UPDATE atómico (`InventoryFacade` → `discountIfAvailable`) | 37 ms | 36 ms | 40 ms | 14 |

Las líneas relevantes incluyen la consulta, el adaptador y el caso de uso, junto con anotaciones y
firmas de esos fragmentos. La actualización atómica fue más rápida en esta carga y tiene menor
superficie de código. Ordering no se modifica.

### Cuándo revisar esta decisión

Revisar esta decisión si la latencia de la reserva directa se vuelve crítica, o si, pasado un
tiempo, la reserva sigue sin más reglas que comparar unidades con el disponible: en ese caso la
ventaja de tener la regla en el dominio no compensa el coste, y el `UPDATE` condicional con
`RETURNING` sería la mejor opción.
