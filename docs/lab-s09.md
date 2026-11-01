# Lab S09

## Paso 4 — paginación estable

> En el paso 8 los guardianes `ProductSearchPaginationIT`, `ProductEditConflictIT` y
> `StockReservationConcurrencyIT` se renombraron a `*Test` para que corran en el gate de fin de
> turno (`verify-quality.sh --fast`). Los comandos de abajo son los que se ejecutaron entonces.

### Experimento rojo: sin desempate por id

Con el desempate por id retirado temporalmente de `ProductSearchRepositoryAdapter`:

```text
bash ./mvnw -q -Dit.test=ProductSearchPaginationIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify
```

El test falló con este resultado de AssertJ:

```text
Expected size: 60 but was: 52
```

El mapa de ocurrencias mostraba ids repetidos, por ejemplo:

```text
85b8cfa4-01f8-3a53-b015-7bdf0ec70574=2L
ac795466-5b92-3f7a-bb86-e382b728de64=2L
b2b1d711-9408-32b9-8ef1-27f13908cb99=2L
```

Por tanto, al recorrer las páginas hubo productos ausentes y otros repetidos. El fallo
se produce porque el top-N heapsort no es estable cuando `LIMIT/OFFSET` ordena por
precios repetidos: PostgreSQL puede devolver los empates en distinto orden entre
páginas.

### Resultado verde: desempate por id restaurado

Se restauró el desempate determinista:

```java
Sort sort = Sort.by(direction, criteria.order().field())
    .and(Sort.by(Sort.Direction.ASC, "id"));
```

El mismo comando terminó correctamente:

```text
Process finished with exit code 0
```

## Deuda: mappers

La regla de mappers se aplicó al buscador y a la creación del catálogo. La deuda restante
es únicamente ordering:

- `OrderJpaEntity.from`.
- `OrderSummaryResponse.from` y `OrderResponse.from`.

## Paso 4 — número de consultas

### Experimento sin `fetchAssociations()`

Se comentó temporalmente la línea `filters.add(ProductSpecifications.fetchAssociations());`
y se ejecutó:

```text
bash ./mvnw -q -Dtest=ProductSearchQueryCountTest test
```

El test no llegó a la aserción AssertJ del número de sentencias. Falló antes con:

```text
org.hibernate.LazyInitializationException: Could not initialize proxy
[CategoryJpaEntity#00000000-0000-0000-0000-000000000001] - no session
```

Por ello, no hubo valores de sentencias esperadas frente a observadas: la conversión
de `CategoryJpaEntity.name()` intentó acceder a una asociación lazy fuera de la sesión.
La sentencia extra no pudo medirse porque el caso terminó antes de ejecutar la
aserción de consultas.

### Resultado verde con `fetchAssociations()` restaurado

Se restauró exactamente la línea:

```java
filters.add(ProductSpecifications.fetchAssociations());
```

El mismo comando terminó correctamente con el umbral original (`<= 2`):

```text
Process finished with exit code 0
```

## Paso 5 — conflicto al guardar

### Experimento rojo: no usar la versión esperada

Se cambió temporalmente el mapper para enviar `version + 1` al adaptador JPA y se ejecutó:

```text
bash ./mvnw -q -Dtest=ProductEditConflictIT test
```

El test determinista falló porque la edición no produjo el conflicto esperado:

```text
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
[ERROR] ProductEditConflictTest.should_reject_when_version_changes_before_save
java.lang.AssertionError:
Expecting code to raise a throwable.
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.6:test
There are test failures.
```

Se restauró el mapper para enviar la versión esperada del producto. Con esa versión, el mismo
test devuelve `ConcurrentProductModificationException` y confirma por JDBC que nombre y precio
siguen siendo los originales.

## Paso 5 — edición con @Version

### Test de API: dos PUT con la misma versión

Se creó un producto por `POST /api/products`, se tomó la versión inicial de la respuesta y se
enviaron dos PUT válidos con esa misma versión:

```text
bash ./mvnw -q -Dtest=ProductEditingIT test
```

El primer PUT devolvió `200 OK` con la edición confirmada y la versión incrementada. El segundo
PUT devolvió `409 Conflict` con ProblemDetail type
`https://api.master-acdc.dev/problems/concurrent-modification`.

El resultado se verificó directamente en la base de datos: el producto conserva el nombre y el
precio del primer PUT; los valores del segundo PUT no se guardan.

## Paso 6 — timeout del bloqueo

Se retuvo la fila de stock con `SELECT ... FOR UPDATE` en otra conexión JDBC sin confirmar y se
ejecutó la reserva a través del caso de uso transaccional:

```text
bash ./mvnw -q -Dtest=StockReservationTransactionIT test
```

El camino que funcionó fue el hint JPA
`jakarta.persistence.lock.timeout=2000`; no fue necesario aplicar el plan B con
`SET LOCAL lock_timeout = '2s'`. PostgreSQL devolvió SQLSTATE `55P03` (`lock_not_available`) y
Spring lo entregó al adaptador como `CannotAcquireLockException`, que se tradujo a
`StockBusyException`.

La reserva falló en aproximadamente 2,0 segundos, dentro del intervalo exigido de 1,5 a 3,5
segundos, y el stock permaneció sin cambios.

## Paso 6 — pesimista vs UPDATE atómico

Se ejecutó `InventoryReservationComparisonIT` con PostgreSQL/Testcontainers. Las dos rutas se
midieron en la misma capa transaccional, sin HTTP, con 50 llamadas concurrentes de una unidad sobre
30 unidades. Antes de las cinco rondas medidas se ejecutó una ronda de calentamiento por ruta.
Cada ronda confirmó 30 reservas exitosas, 20 rechazos por insuficiencia y stock final 0.

```text
bash ./mvnw -q -Dtest=InventoryReservationComparisonIT test
```

| Ruta | Mediana | Mínimo | Máximo | Resultado por ronda |
|---|---:|---:|---:|---|
| Pesimista (`ReserveProductStockUseCase`) | 118 ms | 108 ms | 121 ms | 30/20/0 |
| UPDATE atómico (`InventoryFacade` → `discountIfAvailable`) | 37 ms | 36 ms | 40 ms | 30/20/0 |

Las mediciones observadas fueron `[120, 121, 118, 108, 113]` ms para la ruta pesimista y
`[37, 40, 37, 36, 36]` ms para la ruta atómica. El recuento de líneas relevantes, contando
consulta, adaptador y caso de uso e incluyendo las anotaciones y firmas de esos fragmentos, fue:

| Ruta | Consulta | Adaptador | Caso de uso | Total |
|---|---:|---:|---:|---:|
| Pesimista | 4 | 10 | 6 | 20 |
| UPDATE atómico | 5 | 3 | 6 | 14 |

La ruta atómica fue aproximadamente 3,2 veces más rápida en esta ejecución y requiere seis líneas
relevantes menos. No se modificó código de producción de ordering.
