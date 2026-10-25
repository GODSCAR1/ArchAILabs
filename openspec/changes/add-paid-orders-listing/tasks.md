# Tasks

## 1. Esquema y modelo de persistencia

- [x] 1.1 Crear la migración V4 y la entidad de cliente de lectura, sin clave foránea desde los pedidos; verificar con una prueba de integración que la migración crea la tabla, acepta pedidos con `customerId` huérfano y conserva `name` obligatorio.
- [x] 1.2 Añadir `CustomerJpaEntity` y `CustomerJpaRepository` de solo lectura, sin asociación JPA desde la entidad de pedido, y mantener `customerId` y las líneas EAGER; verificar con `OrderSummaryQueryAdapterIT` que un cliente ausente produce nombre nulo sin inicializar proxies.

## 2. Dominio y aplicación

- [x] 2.1 Crear `OrderSummary`, `OrderSummaryQuery`, `ListOrdersByStatusUseCase` y `ListOrdersByStatusService`; verificar con `ListOrdersByStatusServiceTest` que el caso de uso delega el estado recibido y devuelve los resúmenes del puerto.
- [x] 2.2 Verificar el contrato de resumen y sus métricas en el adaptador con `OrderSummaryQueryAdapterIT`, usando `@DisplayName("Pedidos PAID listados con nombre y métricas")` para el escenario de nombre, número de líneas y total de unidades.

## 3. Adaptador de consulta y transacción

- [x] 3.1 Añadir la búsqueda por estado al repositorio JPA y el adaptador que resuelve cada cliente con `CustomerJpaRepository.findById`, mapea `lines.size()` y suma `quantity`, sin `findAllById`, joins, proyección ni agregación SQL; verificar con `OrderSummaryQueryAdapterIT` y `@DisplayName("Un pedido PENDING no aparece al solicitar PAID")` que el filtro excluye estados distintos.
- [x] 3.2 Ensamblar el caso de uso en `OrderingConfiguration` mediante `TransactionTemplate` read-only; verificar con una prueba de contexto de ordering que el bean se puede invocar y con `@DisplayName("Sin pedidos PAID")` que devuelve una lista vacía sin escribir datos.

## 4. API y errores

- [x] 4.1 Añadir `GET /api/orders` y el DTO HTTP del resumen, validando `status` obligatorio y convirtiéndolo a estado de pedido; verificar con `OrderControllerTest` y `@DisplayName("Pedidos PAID listados con nombre y métricas")` que responde 200 con `id`, `customerName`, `lineCount` y `totalUnits`.
- [x] 4.2 Añadir el `ProblemDetail` para estado inválido; verificar con `OrderControllerTest` y `@DisplayName("Status inválido")` que `?status=FOO` responde 400 con type `https://api.master-acdc.dev/problems/invalid-order-status`.
- [x] 4.3 Añadir el `ProblemDetail` para estado ausente; verificar con `OrderControllerTest` y `@DisplayName("Sin parámetro status")` que la ausencia de `status` responde 400 con type `https://api.master-acdc.dev/problems/missing-order-status`.
- [x] 4.4 Cubrir el cliente ausente en el endpoint; verificar obligatoriamente con una prueba de integración HTTP + PostgreSQL que pase por el `TransactionTemplate` real y use `@DisplayName("Pedido cuyo cliente no existe")`, comprobando 200, `customerName: null` y ausencia de respuesta 500. No usar `@WebMvcTest` para este escenario.
- [x] 4.5 Añadir la prueba web de frontera para comparación exacta; verificar con `OrderControllerTest` y `@DisplayName("Status en minúsculas")` que `?status=paid` responde 400 con type `https://api.master-acdc.dev/problems/invalid-order-status`.

## 5. Integración del contrato

- [x] 5.1 Añadir una prueba de integración HTTP con PostgreSQL/Testcontainers para validar conjuntamente los siete escenarios de `specs/ordering/spec.md`, con los `@DisplayName` exactos: `Pedidos PAID listados con nombre y métricas`, `Un pedido PENDING no aparece al solicitar PAID`, `Sin pedidos PAID`, `Status inválido`, `Status en minúsculas`, `Sin parámetro status` y `Pedido cuyo cliente no existe`. El último debe pasar por el `TransactionTemplate` real.
- [x] 5.2 Ejecutar `bash ./verify-quality.sh --fast` y corregir cualquier fallo de Spotless, Checkstyle, tests o especificaciones; verificar que los flujos existentes de creación y cancelación siguen pasando sin cambios de comportamiento.
