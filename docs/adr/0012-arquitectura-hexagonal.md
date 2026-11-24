# ADR 0012 — Arquitectura hexagonal estricta: el módulo ordering como referencia

- Estado: aceptado (lab S17, 2026-11-24)
- Evidencias y medidas: `docs/lab-s17.md`, `docs/evidencias/s17/`
- Red de seguridad: `OrderApiCharacterizationIT` (zona protegida, `scripts/protected-tests.txt`)

## Contexto

`ordering` ya estaba repartido en `domain`, `application` e `infrastructure` desde labs anteriores,
pero el núcleo tenía fugas:

- `Order` era un `record` con constructor público: cualquiera podía crear un pedido en cualquier estado.
  `ConfirmationConfiguration` lo hacía (`new Order(..., PAID, ...)`) y `ConfirmOrderService` decidía si
  un pedido era confirmable. Las reglas de estado vivían fuera del agregado.
- Los casos de uso recibían `UUID` sueltos y devolvían el agregado `Order` hasta la capa web.
- `PaymentTransactions` llevaba el concepto de transacción al núcleo, y su implementación era una clase
  anónima dentro de un método `@Bean`.
- Había un puerto por tipo de evento (`OrderCancelledPublisher`, `OrderConfirmationOutbox`), y el
  servicio construía el evento en lugar de recogerlo del agregado.

El refactor no podía cambiar la API, el esquema, la fila del outbox ni el evento de cancelación.

## Decisión

### 1. Estructura de paquetes

Se mantiene la convención de todo el repo (no `adapter.*` como en el enunciado del lab):

```
ordering/
├── api/                                  fachadas para otros módulos
├── domain/
│   ├── model/        Order (agregado), OrderLine, OrderStatus, OrderSummary, PaymentAttempt…
│   ├── event/        OrderEvent (sellado), OrderCancelled, OrderConfirmed
│   ├── exception/    excepciones del dominio
│   └── port/         puertos de salida: OrderRepository, OrderPayments, PublishOrderEvents,
│                     PaymentGateway, OrderAmountCalculator, StockReserver, OrderSummaryQuery
├── application/
│   ├── port/         puertos de entrada (<Accion>UseCase), comandos y OrderDetail
│   └── *Service      un servicio por caso de uso, Java puro
└── infrastructure/
    ├── adapters/in/web/       controladores, DTO, OrderWebMapper, Authorized*UseCase, ProblemDetail
    ├── adapters/out/persistence/   JPA y JDBC: OrderRepositoryAdapter, OrderPaymentJdbcAdapter, mappers
    ├── adapters/out/events/   OrderEventsAdapter, relay del outbox
    ├── adapters/out/payment/  RestPaymentGateway
    ├── adapters/out/catalog/, adapters/out/inventory/
    └── config/                @Bean por servicio y por adaptador
```

### 2. Tabla de ubicación (nombre del lab → nombre en el repo)

| Lab | Repo | Dónde |
|---|---|---|
| `PlaceOrder` / `PlaceOrderCommand` / `PlacedOrder` | `CreateOrderUseCase` / `PlaceOrderCommand` / `OrderDetail` | `application/port` |
| `PayOrder` / `PayOrderCommand` / `PaidOrder` | `ConfirmOrderUseCase` / `ConfirmOrderCommand` / `OrderDetail` | `application/port` |
| `CancelOrder` / `CancelOrderCommand` | `CancelOrderUseCase` / `CancelOrderCommand` / `OrderDetail` | `application/port` |
| `RefundOrder` / `RefundOrderCommand` / `RefundedOrder` | `RefundOrderUseCase` / `RefundOrderCommand` / `OrderDetail` | `application/port` |
| `GetOrderDetail` (`OrderId`, `CustomerId`) | `GetOrderUseCase` (`UUID`, `OrderReadScope`) | `application/port` |
| `ListCustomerOrders` | `ListOrdersByStatusUseCase` (el listado real es por estado) | `application/port` |
| `OrderStore` | `OrderRepository` → `OrderRepositoryAdapter` | `domain/port` → `adapters/out/persistence` |
| `AuthorizePayment` / `RefundPayment` | `PaymentGateway` → `RestPaymentGateway` (el reembolso no llama a la pasarela) | `domain/port` → `adapters/out/payment` |
| `PublishOrderEvents` → `OutboxOrderEventPublisher` | `PublishOrderEvents` → `OrderEventsAdapter` | `domain/port` → `adapters/out/events` |
| `LoadProductPrices` | `OrderAmountCalculator` → `CatalogOrderAmountCalculator` | `domain/port` → `adapters/out/catalog` |
| — | `OrderPayments` → `OrderPaymentJdbcAdapter` (intentos de pago atómicos) | `domain/port` → `adapters/out/persistence` |

### 3. Regla de puertos

- **Entrada:** una interfaz por caso de uso. Recibe un comando (o los parámetros de una consulta) y
  devuelve un resultado de aplicación (`OrderDetail`, `OrderSummary`), **nunca el agregado**. Ninguna
  clase de `adapters/in/web` importa `domain.model.Order`.
- **Salida:** un puerto por necesidad del núcleo, con nombres de negocio y sin tipos técnicos
  (ni `Transaction`, ni `JdbcTemplate`, ni entidades JPA). Si una operación debe ser atómica, lo dice su
  contrato y lo garantiza el adaptador (`OrderPayments`: cada método es una operación atómica).
- **Eventos:** el agregado los acumula y el servicio los recoge con `pullEvents()` y los entrega a
  `PublishOrderEvents` **solo si hay alguno**. El adaptador reparte por tipo con un `switch` sobre el
  `OrderEvent` sellado y sin `default`, así que un evento nuevo obliga a decidir su destino.
- **Autorización de objeto:** se resuelve en el adaptador web (`Authorized*UseCase`, `OrderActorResolver`),
  que elige el `OrderReadScope` (propio o `order:read-any`). El núcleo nunca lee el `SecurityContext`.

### 4. Agregado y save explícito

- `Order` es una clase `final` con constructor privado. `Order.place(...)` valida las invariantes (cliente,
  al menos una línea, sin producto repetido; la cantidad positiva la valida `OrderLine`).
  `Order.restore(...)` reconstruye desde persistencia sin reglas de negocio.
- Las transiciones son métodos del negocio que cambian el propio agregado o lanzan la excepción del dominio:
  `confirm()` (PENDING → PAYING), `registerPayment()` (PAYING → PAID, registra `OrderConfirmed`),
  `cancel()` (PENDING/PAID → CANCELLED, registra `OrderCancelled`) y `refund()` (PAID/SHIPPED → REFUNDED).
- Igualdad por id. Los tests comprueban estado y líneas explícitamente, no con `equals`.
- **Save explícito:** nada se persiste por cambiar el agregado. Cada servicio llama al puerto de salida
  (`save`, `cancelIfCancelable`, `refundIfRefundable`, `OrderPayments.settle`).

### 5. Transacciones

- Los servicios no conocen las transacciones. Cada caso de uso se envuelve en `OrderingConfiguration` con
  un `TransactionTemplate` (de solo lectura en las consultas), y por fuera con su decorador `Authorized*`.
- **Excepción: la confirmación.** La llamada HTTP a la pasarela no puede quedar dentro de una transacción.
  `ConfirmOrderService` orquesta tres operaciones atómicas de `OrderPayments` (`startAttempt`,
  `abandonAttempt`, `settle`) con la llamada a la pasarela entre medias. `settle` marca el pedido como
  pagado y publica sus eventos (la fila del outbox) en la misma transacción.
- La protección frente a la concurrencia sigue en las actualizaciones condicionales
  (`... where status = 'PENDING'`): el agregado decide si la transición es válida y la base de datos
  garantiza que solo gane una petición.

## Concesiones aceptadas

1. **Nombres y ubicación de los puertos.** Se conservan los del repo (`*UseCase`, `domain/port`) en lugar de
   `application.port.in/out`, para no romper la convención del resto de módulos ni sus reglas de ArchUnit.
2. **Sin `OrderId` ni `CustomerId`.** Seguimos con `UUID`: el cambio tocaría todos los puertos y
   adaptadores sin aportar una regla nueva.
3. **`OrderDetail` expone `OrderStatus` y `OrderLine`.** Son tipos del dominio, no tipos técnicos; lo que se
   prohíbe es exponer el agregado.
4. **Sin optimistic locking (`@Version`).** Pediría una migración. Las actualizaciones condicionales ya
   protegen las transiciones concurrentes.
5. **Sin plazo de 30 días para reembolsar.** No existía: añadirlo sería un cambio de comportamiento. Va en
   un change aparte.
6. **Reglas de estado duplicadas en SQL.** Las actualizaciones condicionales repiten qué estados permiten
   cada transición. Es la garantía frente a la concurrencia, y los IT de concurrencia la cubren.
7. **`@Nullable` de JSpecify en el dominio.** Es una anotación de nulabilidad sin dependencia de Spring,
   JPA ni Jackson.
8. **Fakes en memoria.** Los tests de servicio siguen con mocks de Mockito sobre los puertos.

## Fugas encontradas y su corrección

| Fuga | Dónde estaba | Corrección |
|---|---|---|
| Estado decidido fuera del agregado | `new Order(..., PAID, ...)` en `ConfirmationConfiguration`; comprobación de PENDING en `ConfirmOrderService` | `confirm()` y `registerPayment()` en `Order` |
| Invariantes saltables | Constructor público del `record` `Order` | Clase `final`, constructor privado, `place`/`restore` |
| El agregado llega a la web | Casos de uso que devolvían `Order` | Comandos y `OrderDetail` |
| Tipo técnico en un puerto | `PaymentTransactions` y su clase anónima en un `@Bean` | `OrderPayments` con operaciones atómicas en un adaptador con nombre |
| Eventos construidos por el servicio | `new OrderCancelled(...)` en `CancelOrderService`; un puerto por tipo de evento | `pullEvents()` y un único `PublishOrderEvents` |

## Consecuencias

- El dominio y los servicios se prueban sin Spring. Las medidas antes/después están en `docs/lab-s17.md`.
- `OrderApiCharacterizationIT` fija la API, los errores y la fila del outbox. Si cambia en un refactor, se
  ha cambiado el comportamiento: se vuelve atrás.
- Bug encontrado y no corregido aquí: el orden de las líneas de un pedido no está garantizado (ver
  `docs/lab-s17.md`).
- Hasta tener reglas de ArchUnit para todo esto, `verify-quality.sh` falla si `domain` o `application`
  importan `jakarta.persistence`, `org.springframework` o Jackson.
