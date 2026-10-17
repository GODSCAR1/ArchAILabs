# Tasks

## 1. Dominio de ordering: estado, transición, evento y puertos

- [x] 1.1 Añadir `CANCELLED` a `OrderStatus` y `Order.cancel()` con validación de transición (PENDING/PAID → CANCELLED; SHIPPED/CANCELLED → `OrderNotCancellableException`). Verificación: `OrderTest.should_return_cancelled_order_when_cancel_from_pending`, `should_return_cancelled_order_when_cancel_from_paid`, `should_throw_order_not_cancellable_when_cancel_from_shipped` y `should_throw_order_not_cancellable_when_cancel_from_cancelled`.
- [x] 1.2 Crear el evento de dominio `OrderCancelled(orderId, lines)`, el puerto `OrderCancelledPublisher` y las excepciones `OrderNotFoundException` / `OrderNotCancellableException` en `ordering/domain`. Verificación: compila sin dependencias de framework; el comportamiento se prueba en 4.2 y 5.1.
- [x] 1.3 Añadir al puerto `OrderRepository` los métodos `findByIdAndCustomerId(orderId, customerId)` y `cancelIfCancelable(orderId, customerId)`. Verificación: compila; implementación y pruebas en 3.1.

## 2. inventory: liberación de stock

- [x] 2.1 Crear `ReleaseStockUseCase`, `ReleaseStockService` (incrementa por línea, ordenado por `productId`) y el puerto de dominio `StockRepository.release(productId, quantity)`; añadir `InventoryFacade.release(lines)` con su `TransactionTemplate`. Verificación: `ReleaseStockServiceTest.should_increase_available_stock_when_release_lines` y `should_ignore_ordering_of_lines_when_release`.
- [x] 2.2 Implementar `release` en `StockJpaRepository` (`UPDATE stock SET available = available + :quantity WHERE product_id = :productId`) y en `StockRepositoryAdapter` (0 filas afectadas ⇒ lanza excepción de dominio). Verificación: `InventoryRepositoryAdapterIT` con `@DisplayName("Liberación de stock de varias líneas")` (GIVEN A=7, B=3; liberar (A,3),(B,2); THEN A=10, B=5) y `should_throw_when_release_affects_no_row` (sin `@DisplayName` de escenario).
- [x] 2.3 Añadir al IT de fachada el escenario de reserva posterior. Verificación: `InventoryFacadeIT` con `@DisplayName("Stock liberado vuelve a estar disponible para reservar")` (GIVEN A=7 y liberada (A,3); WHEN reservar (A,10); THEN stock 0, nunca negativo).

## 3. Persistencia de ordering: lookup y cancelación condicional

- [x] 3.1 Añadir `OrderJpaEntity.toDomain()` y los métodos nuevos en `OrderRepositoryAdapter` / `OrderJpaRepository` (query derivada por id+customer y `@Modifying` con `WHERE status IN ('PENDING','PAID')`). Verificación: `OrderRepositoryAdapterIT.should_find_order_by_id_and_customer_id_when_owned` y `should_cancel_order_when_status_is_pending` (y `should_not_cancel_order_when_status_is_shipped`, frontera a cada lado).

## 4. Caso de uso de cancelación, publicación y listener

- [x] 4.1 Crear `SpringOrderCancelledPublisher` (adaptador de `ApplicationEventPublisher`) y `OrderCancelledListener` en `ordering/infrastructure/adapters/out/inventory` (`@EventListener` → `InventoryFacade.release`); ensamblar ambos beans en `OrderingConfiguration` con `@Bean`. Verificación: compila y `OrderTransactionIT.should_rollback_order_and_stock_when_release_fails` (nuevo: si `release` lanza, el pedido no queda CANCELLED y el stock no cambia).
- [x] 4.2 Crear `CancelOrderUseCase` (devuelve el pedido CANCELLED) + `CancelOrderService` (404 → validación de dominio → update condicional → publicar) y el bean envuelto en `TransactionTemplate` en `OrderingConfiguration`. Verificación: `CancelOrderServiceTest.should_cancel_and_publish_when_order_pending`, `should_report_not_found_when_order_absent_or_foreign`, `should_report_not_cancellable_when_order_shipped`, `should_report_not_cancellable_when_update_matches_no_row` y `should_not_publish_when_order_not_found`.

## 5. Web: endpoint y mapeo de errores

- [x] 5.1 Añadir `POST /api/orders/{id}/cancellation` en `OrderController` (path `{id}` como `String`: no-UUID ⇒ `OrderNotFoundException`; reutiliza `parseCustomerId`, responde 200 con `OrderResponse` del pedido devuelto por el caso de uso) y los handlers 404 `order-not-found` / 409 `order-not-cancellable` en `OrderExceptionHandler`. Verificación: `OrderControllerTest.should_respond_400_missing_customer_id_when_header_absent_on_cancel`, `should_respond_400_invalid_customer_id_when_header_not_uuid_on_cancel`, `should_respond_404_order_not_found_when_order_id_not_uuid`, `should_respond_404_order_not_found_when_use_case_throws`, `should_respond_409_order_not_cancellable_when_use_case_throws` y `should_respond_200_cancelled_when_use_case_returns`.

## 6. Integración extremo a extremo contra Postgres real

- [x] 6.1 Crear `OrderCancellationIT` (`@SpringBootTest` + Testcontainers PostgreSQL + MockMvc, datos concretos con `@Sql`, stock asertado con `JdbcTemplate`) con los escenarios cancelables: `@DisplayName("Pedido PENDING cancelado libera stock de todas sus líneas")` (pedido con (A,3),(B,2); stock final A=10, B=10) y `@DisplayName("Frontera: pedido PAID cancelable")` (PAID con (A,1); stock 10).
- [x] 6.2 Añadir al IT los rechazos por estado: `@DisplayName("Frontera: pedido SHIPPED no cancelable")` (sigue SHIPPED, stock intacto) y `@DisplayName("Pedido ya cancelado")` (sigue CANCELLED, stock intacto), ambas con type `.../order-not-cancellable`.
- [x] 6.3 Añadir al IT los rechazos por propiedad: `@DisplayName("Pedido inexistente")` (404 `.../order-not-found`, stock intacto), `@DisplayName("Cancelación con id de pedido que no es UUID")` (path `no-uuid`, 404 `.../order-not-found`, stock intacto) y `@DisplayName("Pedido de otro cliente")` (404, pedido ajeno PENDING, stock intacto).
- [x] 6.4 Añadir al IT los rechazos de cabecera: `@DisplayName("Cancelación sin cabecera X-Customer-Id")` (400 `.../missing-customer-id`) y `@DisplayName("Cancelación con X-Customer-Id que no es UUID")` (400 `.../invalid-customer-id`), pedido y stock sin cambios.
- [x] 6.5 Añadir al IT los escenarios de precedencia: `@DisplayName("Precedencia: cabecera antes que existencia")` (400 aunque el pedido no exista) y `@DisplayName("Precedencia: propiedad antes que estado")` (404 para pedido SHIPPED ajeno).
- [x] 6.6 Añadir al IT la concurrencia: `@DisplayName("Dos cancelaciones concurrentes del mismo pedido")` (ExecutorService + latches como en `OrderPlacementIT`; exactamente una 200, la otra 409 `.../order-not-cancellable`, stock liberado una sola vez).

## 7. ArchUnit y verificación final

- [x] 7.1 Añadir `.because(...)` a las 7 reglas existentes de `ArchitectureTest` y crear la regla `ordering_domain_events_are_only_used_by_ordering`. Verificación: `bash ./mvnw -q test` en verde (ArchitectureTest pasa).
- [x] 7.2 Ejecutar `bash ./verify-quality.sh --full` (los escenarios viven en IT, que `--fast` no ejecuta) con salida en verde.
