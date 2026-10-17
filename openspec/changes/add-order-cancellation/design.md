# Design

## Context

Hoy `ordering` solo crea pedidos: `Order` es un record inmutable con estado PENDING (`OrderStatus` = PENDING, PAID, SHIPPED), `OrderRepository` solo tiene `save`, y no existe ninguna consulta por id. `inventory` solo expone reserva (`InventoryFacade.reserve`, con `discountIfAvailable` como actualización condicional) y no tiene operación de incremento de stock. No hay eventos de dominio en el repo: la comunicación ordering→inventory es una llamada directa desde `StockReserverAdapter` (infraestructura de ordering) a `inventory.api.InventoryFacade`. La transacción se gestiona con `TransactionTemplate` programático en `infrastructure/config` (sin `@Transactional` en código principal), y `OrderJpaEntity` no tiene `@Version` ni mapper `toDomain()`. El estado del pedido es `VARCHAR(16)`, así que CANCELLED no requiere migración. Ver proposal.md - Why.

## Goals / Non-Goals

**Goals:**

- Cancelación completa del pedido con liberación de stock en la misma transacción, sincrónica y antes del commit.
- Desacoplamiento ordering→inventory mediante evento de dominio `OrderCancelled` consumido por un listener en infraestructura de ordering.
- Exactamente-una liberación ante cancelaciones concurrentes (actualización condicional del estado).
- Reglas ArchUnit con `.because(...)`, nuevas y existentes.

**Non-Goals:**

- (Además de los de proposal.md) No se introduce outbox, ni entrega asíncrona, ni caché de eventos: el evento es un mecanismo interno de desacoplamiento en el mismo hilo/transacción, no un canal persistente.

## Decisions

### D1: Estado CANCELLED y transición en dominio

`OrderStatus` añade `CANCELLED`. `Order` gana `Order.cancel()`, que valida la transición: si el estado es PENDING o PAID devuelve un nuevo `Order` con estado CANCELLED (resto de campos conservados); si es SHIPPED o CANCELLED lanza `OrderNotCancellableException`. El record sigue inmutable. El UPDATE condicional (D2) se mantiene además como defensa de concurrencia: la validación de dominio decide sobre la instantánea leída, el update decide sobre el estado real.

*Alternativa descartada:* mutar el campo `status` del record (rompe inmutabilidad y el patrón de `create`); dejar la validación solo en el update (el dominio no expresaría la regla de transición y `cancel()` sería incondicional).

### D2: Cancelación condicional en una sola sentencia (clave de concurrencia)

Nuevo método en el puerto `OrderRepository`:

```java
boolean cancelIfCancelable(UUID orderId, UUID customerId);
```

Implementado con actualización condicional (JPQL/`@Modifying`):

```sql
UPDATE orders SET status = 'CANCELLED'
 WHERE id = :orderId AND customer_id = :customerId
   AND status IN ('PENDING', 'PAID')
```

Devuelve `true` si afectó a 1 fila. Solo la cancelación que gana la carrera puede publicar el evento y liberar stock; la pierde recibe 409 sin tocar stock. No se usa `@Version`/optimista con excepción: el update condicional es el mismo estilo que `discountIfAvailable` ya usado en inventory.

Flujo del caso de uso `CancelOrderService.cancel(orderId, customerId)` (devuelve el pedido cancelado):

1. `orderRepository.findByIdAndCustomerId(orderId, customerId)` → vacío ⇒ `OrderNotFoundException` (404, cubre inexistente y ajeno sin distinguir).
2. `order.cancel()` ⇒ estado SHIPPED o CANCELLED en la instantánea leída ⇒ `OrderNotCancellableException` (409, validación de dominio).
3. `orderRepository.cancelIfCancelable(orderId, customerId)` ⇒ `false` ⇒ `OrderNotCancellableException` (409; cubre la carrera perdida contra otra cancelación).
4. `true` ⇒ `orderCancelledPublisher.publish(new OrderCancelled(orderId, lines))` y se devuelve el `Order` CANCELLED del paso 2.

*Alternativa descartada:* leer estado y decidir en Java (race entre lectura y escritura: dos hilos podrían publicar el evento); doble sentencia SELECT+UPDATE sin cláusula de estado en el WHERE (mismo problema).

### D3: Pedido - lookup por id y propiedad

`OrderRepository` añade `Optional<Order> findByIdAndCustomerId(UUID orderId, UUID customerId)`. `OrderJpaEntity` gana `toDomain()` (mapea `status` a `OrderStatus.valueOf` y las líneas del `@ElementCollection` EAGER); `OrderRepositoryAdapter` implementa ambos métodos nuevos con `OrderJpaRepository` (query derivada `findByIdAndCustomerId` + `@Modifying` JPQL para el update, con `flush` posterior).

### D4: Evento de dominio y publicación sin Spring en application

- `ordering/domain/event/OrderCancelled.java`: record `OrderCancelled(UUID orderId, List<OrderLine> lines)`. Vive en dominio (el evento es un hecho de negocio) y no depende de framework.
- `ordering/domain/port/OrderCancelledPublisher.java`: `void publish(OrderCancelled event)`.
- `CancelOrderService` recibe el puerto por constructor y lo invoca dentro de su ejecución (que ya está envuelta en `TransactionTemplate` por el bean de config, igual que `createOrderUseCase`).
- Adaptador `ordering/infrastructure/adapters/out/events/SpringOrderCancelledPublisher` envuelve `ApplicationEventPublisher` y hace `publishEvent`.

Así la publicación ocurre **dentro** del bloque `TransactionTemplate.execute` (antes del commit), sin que `application/` conozca Spring. El bean se ensambla en `OrderingConfiguration` (solo `@Bean`, sin lógica).

*Alternativa descartada:* publicar desde el wrapper de config después de `execute` (el commit ya habría ocurrido, el listener abriría una transacción nueva); `ApplicationEventPublisher` directamente en el servicio (Spring en `application/`, prohibido por ArchUnit).

### D5: Listener síncrono que libera stock vía inventory.api

- `OrderCancelledListener` en `ordering/infrastructure/adapters/out/inventory` (junto a `StockReserverAdapter`): `@EventListener` (sincrónico por defecto, mismo hilo ⇒ misma transacción activa) que convierte `OrderCancelled` a `List<StockLine>` y llama a `InventoryFacade.release(lines)`.
- `inventory/api/InventoryFacade.release(List<StockLine>)`: nuevo método público, gemelo de `reserve`; envuelve `ReleaseStockUseCase.execute(lines)` en su `TransactionTemplate` (REQUIRED ⇒ se adhiere a la transacción de ordering).
- `inventory/application/port/ReleaseStockUseCase` + `ReleaseStockService`: por cada línea, `stockRepository.release(productId, quantity)`; ordenado por `productId` como en `ReserveStockService`.
- `inventory/domain/port/StockRepository` añade `void release(UUID productId, int quantity)`; implementación JPQL: `UPDATE stock SET available = available + :quantity WHERE product_id = :productId`. El CHECK `available >= 0` se sigue cumpliendo (solo incrementa). Si la sentencia afecta a 0 filas (producto inexistente), el adaptador lanza excepción de dominio: es un invariante roto (toda línea de un pedido reservado referencia un producto con stock), y lanzar revierte la transacción completa, estado del pedido incluido.

`inventory` no conoce `OrderCancelled` ni a ordering: solo ve una operación de su propio dominio. La conversión evento→`StockLine` vive en el lado de ordering.

*Alternativa descartada:* `StockReleaser` paralelo a `StockReserver` con su propia fachada (duplica el mismo camino; una operación en la fachada basta).

### D6: Web: controller, precedencia y ProblemDetail

- `OrderController` añade `POST /api/orders/{id}/cancellation` con cabecera `X-Customer-Id`, reutilizando `parseCustomerId` (400 `missing-customer-id` / `invalid-customer-id` ya resueltos antes de tocar BD ⇒ precedencia cabecera→existencia→estado). El path `{id}` se recibe como `String` y se parsea a UUID a mano: si no es un UUID se lanza `OrderNotFoundException` (404 `order-not-found`, mismo trato que un pedido inexistente; un 400 de conversión de tipo revelaría que el formato del id importa).
- Respuesta 200: `OrderResponse.from(cancelled)`, con el pedido devuelto por el caso de uso (ya validado y en estado CANCELLED).
- `OrderExceptionHandler` añade: `OrderNotFoundException` → 404 type `.../order-not-found`; `OrderNotCancellableException` → 409 type `.../order-not-cancellable`, con el helper `problem(...)` existente.
- Excepciones nuevas en `ordering/domain/exception`: `OrderNotFoundException`, `OrderNotCancellableException`.

### D7: Transacción extremo a extremo

El bean de config de ordering envuelve `CancelOrderService.cancel` en `TransactionTemplate` (igual que `createOrderUseCase`). Dentro de la misma transacción: update condicional → publicación → `InventoryFacade.release` (REQUIRED). Si la liberación lanzara excepción, todo se revierte (estado incluido). No hay llamadas remotas: es Java en proceso, así que el modo *síncrono antes del commit* no tiene timeouts ni compensación que prever.

## Reglas de arquitectura

ArchUnit (`ArchitectureTest`), con `.because(...)` en **todas** las reglas (las 7 existentes reciben su `.because(...)`; las nuevas se listan abajo):

- (existente) `inventory_domain_has_no_framework_dependencies`
- (existente) `ordering_domain_has_no_framework_dependencies` — cubre también `OrderCancelled` al vivir en `ordering.domain.event`
- (existente) `inventory_application_depends_only_on_domain`
- (existente) `ordering_application_depends_only_on_domain` — `CancelOrderService` solo ve puertos de dominio
- (existente) `inventory_api_is_only_used_by_ordering_infrastructure` — `OrderCancelledListener` y `StockReserverAdapter` son ordering.infrastructure
- (existente) `inventory_does_not_depend_on_ordering`
- (existente) `no_application_context_get_bean_outside_config`
- **(nueva)** `ordering_domain_events_are_only_used_by_ordering`: `noClasses().that().resideOutsideOfPackage("com.codeja.master_acdc.ordering..").should().dependOnClassesThat().resideInAPackage("com.codeja.master_acdc.ordering.domain.event..")` — prefijos completos (`com.codeja.master_acdc...`): con el paquete corto `ordering..` ArchUnit no casa con ninguna clase y la regla pasaría siempre. El evento es interno de ordering; el desacople entre módulos es la fachada, no el tipo del evento.

Dependencias que quedan fijadas por estas reglas: `ordering.(domain|application)` → solo `domain` propio; `ordering.infrastructure` → `ordering.domain` + `inventory.api` (+ frameworks); `inventory.*` → solo `inventory.*` (+ frameworks), nunca `ordering.*`.

## Risks / Trade-offs

- [Al releer el pedido con `toDomain`, el enum `OrderStatus.valueOf` fallaría con estados futuros desconocidos] → mismo acoplamiento que ya existe al escribir (`String status`); se mitigaría al introducir un convertidor JPA, fuera de este cambio.
- [Desplegar el código viejo contra BD con filas CANCELLED haría fallar la lectura de esos pedidos (enum sin CANCELLED)] → aceptado: rollback = redeploy de la versión anterior retirando también las cancelaciones; no hay conversión de datos que preservar en este ejercicio.
- [El listener `@EventListener` es síncrono por convención de Spring, no por tipo] → se documenta en design; una futura anotación `@Async` rompería la atomicidad y sería detectable por la ausencia de rollback del stock, que los tests de integración cubren.
- [Dos cancelaciones concurrentes: la perdedora ve 409 aunque el pedido "existe y es cancelable" en su instantáne lectura] → comportamiento exigido por el spec (exactamente-una); el update condicional lo garantiza sin locks explícitos.
- [Incremento de stock sin tope superior podría enmascarar un doble release de otra bug] → el spec de concurrencia verifica que el stock final es exactamente previo+cantidades; sin outbox no hay redeliveries.

## Migration Plan

Sin migraciones Flyway (el `VARCHAR(16)` de `status` ya admite `CANCELLED`). Despliegue atómico de ordering+inventory juntos (misma app). Rollback: redeploy de la versión anterior; los pedidos ya CANCELLED quedan como datos históricos que la versión anterior no puede leer (ver riesgo).

## Open Questions

- Ninguna: las dudas de enfoque (evento vs. llamada directa, condición de carrera, dónde vive la conversión a `StockLine`) están resueltas en D2–D5.
