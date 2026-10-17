# Spec Delta

## ADDED Requirements

### Requirement: Cancelación de pedido cancelable
El sistema SHALL aceptar la cancelación de un pedido propio mediante `POST /api/orders/{id}/cancellation` identificando al cliente con la cabecera `X-Customer-Id` (UUID), cuando el pedido esté en estado PENDING o PAID. El sistema SHALL responder 200 con el pedido en estado CANCELLED y SHALL liberar el stock reservado de todas sus líneas.

#### Scenario: Pedido PENDING cancelado libera stock de todas sus líneas
- **GIVEN** el producto A con stock 7, el producto B con stock 8 y un pedido PENDING del cliente con id `550e8400-e29b-41d4-a716-446655440000` con las líneas (A, 3) y (B, 2)
- **WHEN** el cliente envía la cancelación con `X-Customer-Id` igual al del pedido
- **THEN** el sistema responde 200 con el pedido: `customerId` igual al de la cabecera, `status` CANCELLED y las líneas (A, 3) y (B, 2)
- **AND** el pedido existe en base de datos en estado CANCELLED
- **AND** el stock de A queda 10 y el de B queda 10

#### Scenario: Frontera: pedido PAID cancelable
- **GIVEN** el producto A con stock 9 y un pedido PAID del cliente con id `550e8400-e29b-41d4-a716-446655440000` con la línea (A, 1)
- **WHEN** el cliente envía la cancelación con `X-Customer-Id` igual al del pedido
- **THEN** el sistema responde 200 con el pedido en estado CANCELLED
- **AND** el stock de A queda 10

### Requirement: Rechazo de pedido no cancelable
El sistema SHALL rechazar la cancelación con código 409 y ProblemDetail type `https://api.master-acdc.dev/problems/order-not-cancellable` cuando el pedido propio esté en estado SHIPPED o ya esté CANCELLED. Ante este rechazo, el pedido conserva su estado y ningún stock queda modificado.

#### Scenario: Frontera: pedido SHIPPED no cancelable
- **GIVEN** el producto A con stock 9 y un pedido SHIPPED del cliente con id `550e8400-e29b-41d4-a716-446655440000` con la línea (A, 1)
- **WHEN** el cliente envía la cancelación con `X-Customer-Id` igual al del pedido
- **THEN** el sistema responde 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`
- **AND** el pedido sigue en estado SHIPPED y el stock de A sigue en 9

#### Scenario: Pedido ya cancelado
- **GIVEN** el producto A con stock 10 y un pedido CANCELLED del cliente con id `550e8400-e29b-41d4-a716-446655440000` con la línea (A, 3)
- **WHEN** el cliente envía la cancelación con `X-Customer-Id` igual al del pedido
- **THEN** el sistema responde 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`
- **AND** el pedido sigue en estado CANCELLED y el stock de A sigue en 10

### Requirement: Cancelación de pedido inexistente o ajeno
El sistema SHALL rechazar la cancelación con código 404 y ProblemDetail type `https://api.master-acdc.dev/problems/order-not-found` cuando el pedido no exista o pertenezca a otro cliente, sin revelar cuál de los dos casos es. Ante este rechazo, ningún pedido y ningún stock quedan modificados.

#### Scenario: Pedido inexistente
- **GIVEN** el producto A con stock 10 y un pedido con id `00000000-0000-0000-0000-000000000000` que no existe
- **WHEN** el cliente con id `550e8400-e29b-41d4-a716-446655440000` envía la cancelación con `X-Customer-Id` válido
- **THEN** el sistema responde 404 con type `https://api.master-acdc.dev/problems/order-not-found`
- **AND** no se modifica ningún pedido y el stock de A sigue en 10

#### Scenario: Cancelación con id de pedido que no es UUID
- **GIVEN** el producto A con stock 10
- **WHEN** el cliente con id `550e8400-e29b-41d4-a716-446655440000` envía la cancelación del pedido con id de path `no-uuid` con `X-Customer-Id` válido
- **THEN** el sistema responde 404 con type `https://api.master-acdc.dev/problems/order-not-found`
- **AND** no se modifica ningún pedido y el stock de A sigue en 10

#### Scenario: Pedido de otro cliente
- **GIVEN** el producto A con stock 9 y un pedido PENDING del cliente con id `660e8400-e29b-41d4-a716-446655440000` con la línea (A, 1)
- **WHEN** el cliente con id `550e8400-e29b-41d4-a716-446655440000` envía la cancelación con `X-Customer-Id` distinto al del pedido
- **THEN** el sistema responde 404 con type `https://api.master-acdc.dev/problems/order-not-found`
- **AND** el pedido ajeno sigue en estado PENDING y el stock de A sigue en 9

### Requirement: Identificación de cliente obligatoria en cancelación
El sistema SHALL rechazar la cancelación con código 400 y ProblemDetail type `https://api.master-acdc.dev/problems/missing-customer-id` cuando falte la cabecera `X-Customer-Id`, y con código 400 y type `https://api.master-acdc.dev/problems/invalid-customer-id` cuando la cabecera no sea un UUID. Ante estos rechazos, ningún pedido y ningún stock quedan modificados.

#### Scenario: Cancelación sin cabecera X-Customer-Id
- **GIVEN** el producto A con stock 9 y un pedido PENDING del cliente con id `550e8400-e29b-41d4-a716-446655440000` con la línea (A, 1)
- **WHEN** el cliente envía la cancelación sin la cabecera `X-Customer-Id`
- **THEN** el sistema responde 400 con type `https://api.master-acdc.dev/problems/missing-customer-id`
- **AND** el pedido sigue en estado PENDING y el stock de A sigue en 9

#### Scenario: Cancelación con X-Customer-Id que no es UUID
- **GIVEN** el producto A con stock 9 y un pedido PENDING del cliente con id `550e8400-e29b-41d4-a716-446655440000` con la línea (A, 1)
- **WHEN** el cliente envía la cancelación con `X-Customer-Id` = `no-es-uuid`
- **THEN** el sistema responde 400 con type `https://api.master-acdc.dev/problems/invalid-customer-id`
- **AND** el pedido sigue en estado PENDING y el stock de A sigue en 9

### Requirement: Precedencia de errores de cancelación
Cuando una petición de cancelación acumule varios errores, el sistema SHALL aplicar la precedencia: cabecera (400) antes que existencia y propiedad del pedido (404) antes que estado del pedido (409). Ante cualquier rechazo, ningún pedido y ningún stock quedan modificados.

#### Scenario: Precedencia: cabecera antes que existencia
- **GIVEN** un pedido con id `00000000-0000-0000-0000-000000000000` que no existe
- **WHEN** se envía la cancelación sin la cabecera `X-Customer-Id`
- **THEN** el sistema responde 400 con type `https://api.master-acdc.dev/problems/missing-customer-id`
- **AND** no se modifica ningún pedido y ningún stock

#### Scenario: Precedencia: propiedad antes que estado
- **GIVEN** un pedido SHIPPED del cliente con id `660e8400-e29b-41d4-a716-446655440000`
- **WHEN** el cliente con id `550e8400-e29b-41d4-a716-446655440000` envía la cancelación con `X-Customer-Id` válido
- **THEN** el sistema responde 404 con type `https://api.master-acdc.dev/problems/order-not-found`
- **AND** el pedido ajeno sigue en estado SHIPPED y ningún stock queda modificado

### Requirement: Concurrencia de cancelaciones del mismo pedido
Cuando dos cancelaciones del mismo pedido se procesen concurrentemente, el sistema SHALL aplicar exactamente una cancelación con liberación de stock; la otra SHALL recibir 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`, sin modificar el pedido ni el stock una segunda vez.

#### Scenario: Dos cancelaciones concurrentes del mismo pedido
- **GIVEN** el producto A con stock 9 y un pedido PENDING del cliente con id `550e8400-e29b-41d4-a716-446655440000` con la línea (A, 1)
- **WHEN** dos cancelaciones del mismo pedido se envían concurrentemente con `X-Customer-Id` igual al del pedido
- **THEN** exactamente una recibe 200 y el pedido queda CANCELLED con el stock de A en 10
- **AND** la otra recibe 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable` y el stock de A no se libera dos veces
