# ordering Specification

## Purpose
Permite a un cliente crear pedidos con líneas de producto y cantidad, reservando stock de forma todo o nada, y define los rechazos de la API con ProblemDetail.

## Requirements

### Requirement: Creación de pedido válido
El sistema SHALL aceptar pedidos en `POST /api/orders` identificando al cliente con la cabecera `X-Customer-Id` (UUID) y con una o varias líneas (producto y cantidad). Si todas las líneas tienen stock reservado con éxito, el sistema SHALL crear el pedido en estado PENDING y responder 201 con el cuerpo `id`, `customerId`, `status` y `lines`, sin cabecera `Location`. Los estados PAID y SHIPPED existen en el dominio pero no son alcanzables mediante la API en este cambio.

#### Scenario: Pedido creado con stock disponible
- **GIVEN** el producto A con stock 10 y un cliente con id `550e8400-e29b-41d4-a716-446655440000`
- **WHEN** el cliente envía un pedido con `X-Customer-Id` válido y la línea (A, 3)
- **THEN** el sistema responde 201 con el pedido: `customerId` igual al de la cabecera, `status` PENDING, la línea (A, 3) y un `id`
- **AND** no hay cabecera `Location`
- **AND** el pedido existe en base de datos con esas líneas y el stock de A queda 7

#### Scenario: Frontera: cantidad igual al stock disponible
- **GIVEN** el producto A con stock 10
- **WHEN** el cliente envía un pedido con la línea (A, 10)
- **THEN** el sistema responde 201 con el pedido PENDING
- **AND** el pedido existe en base de datos y el stock de A queda 0

#### Scenario: Frontera: cantidad una unidad por encima del stock
- **GIVEN** el producto A con stock 10
- **WHEN** el cliente envía un pedido con la línea (A, 11)
- **THEN** el sistema responde 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`
- **AND** no se crea ningún pedido y el stock de A sigue en 10

### Requirement: Identificación de cliente obligatoria
El sistema SHALL rechazar la creación de pedido con código 400 y ProblemDetail type `https://api.master-acdc.dev/problems/missing-customer-id` cuando falte la cabecera `X-Customer-Id`. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Sin cabecera X-Customer-Id
- **GIVEN** el producto A con stock 10
- **WHEN** el cliente envía un pedido sin la cabecera `X-Customer-Id` con la línea (A, 3)
- **THEN** el sistema responde 400 con type `https://api.master-acdc.dev/problems/missing-customer-id`
- **AND** no se crea ningún pedido y el stock de A sigue en 10

### Requirement: Identificación de cliente con UUID válido
El sistema SHALL rechazar la creación de pedido con código 400 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-customer-id` cuando la cabecera `X-Customer-Id` no sea un UUID. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: X-Customer-Id que no es UUID
- **GIVEN** el producto A con stock 10
- **WHEN** el cliente envía un pedido con `X-Customer-Id` = `no-es-uuid` y la línea (A, 3)
- **THEN** el sistema responde 400 con type `https://api.master-acdc.dev/problems/invalid-customer-id`
- **AND** no se crea ningún pedido y el stock de A sigue en 10

### Requirement: Pedido con al menos una línea
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/empty-order` cuando el pedido no lleve ninguna línea. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Pedido sin líneas
- **GIVEN** un cliente con `X-Customer-Id` válido
- **WHEN** el cliente envía un pedido sin líneas
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/empty-order`
- **AND** no se crea ningún pedido y ningún stock queda modificado

### Requirement: Sin producto repetido entre líneas
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/duplicate-product` cuando el mismo producto aparezca en dos líneas distintas. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Producto repetido en dos líneas
- **GIVEN** el producto A con stock 10
- **WHEN** el cliente envía un pedido con las líneas (A, 2) y (A, 3)
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/duplicate-product`
- **AND** no se crea ningún pedido y el stock de A sigue en 10

### Requirement: Rechazo de cantidad no positiva
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-quantity` cuando una línea tenga cantidad ≤ 0. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Cantidad cero o negativa
- **GIVEN** el producto A con stock 10 y el producto B con stock 5
- **WHEN** el cliente envía un pedido con las líneas (A, 0) y (B, 2)
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/invalid-quantity`
- **AND** no se crea ningún pedido y el stock de A y B sigue sin cambios

### Requirement: Producto obligatorio en cada línea
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-line` cuando una línea no lleve `productId`. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Línea sin producto
- **GIVEN** el producto A con stock 10
- **WHEN** el cliente envía un pedido con la línea (sin `productId`, 2) y la línea (A, 1)
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/invalid-line`
- **AND** no se crea ningún pedido y el stock de A sigue en 10

### Requirement: Rechazo de producto desconocido en inventario
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/unknown-product` cuando una línea referencie un producto sin registro de stock en inventario. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Pedido con producto desconocido
- **GIVEN** el producto A con stock 10 y el producto X sin registro de stock
- **WHEN** el cliente envía un pedido con las líneas (A, 3) y (X, 1)
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/unknown-product`
- **AND** no se crea ningún pedido y el stock de A sigue en 10

### Requirement: Rechazo por stock insuficiente (todo o nada)
El sistema SHALL rechazar la creación completa del pedido con código 409 y ProblemDetail type `https://api.master-acdc.dev/problems/insufficient-stock` cuando alguna línea no tiene stock suficiente. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado, incluido el de las líneas que sí tenían stock suficiente.

#### Scenario: Una línea sin stock suficiente
- **GIVEN** el producto A con stock 10 y el producto B con stock 1
- **WHEN** el cliente envía un pedido con las líneas (A, 3) y (B, 2)
- **THEN** el sistema responde 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`
- **AND** no se crea ningún pedido y el stock de A y B sigue en 10 y 1 respectivamente

### Requirement: Precedencia de errores de validación
Cuando una petición acumule varios errores, el sistema SHALL aplicar la precedencia: cabecera (400) antes que cuerpo (422) antes que stock (409). Ante cualquier rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Precedencia: error de cabecera antes que cuerpo y stock
- **GIVEN** el producto A con stock 10 y el producto B con stock 1
- **WHEN** el cliente envía un pedido sin cabecera `X-Customer-Id` con la línea (A, 0), que tiene cantidad no positiva, y la línea (B, 2), que no tiene stock suficiente
- **THEN** el sistema responde 400 con type `https://api.master-acdc.dev/problems/missing-customer-id`
- **AND** no se crea ningún pedido y el stock de A y B sigue en 10 y 1 respectivamente

#### Scenario: Precedencia: error de cuerpo antes que stock
- **GIVEN** el producto A con stock 10 y el producto B con stock 1
- **WHEN** el cliente envía un pedido con `X-Customer-Id` válido con la línea (A, 0), que tiene cantidad no positiva, y la línea (B, 2), que no tiene stock suficiente
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/invalid-quantity`
- **AND** no se crea ningún pedido y el stock de A y B sigue en 10 y 1 respectivamente

#### Scenario: Precedencia: producto desconocido antes que stock insuficiente
- **GIVEN** el producto B con stock 1 y el producto X sin registro de stock
- **WHEN** el cliente envía un pedido con `X-Customer-Id` válido con la línea (B, 2), que no tiene stock suficiente, y la línea (X, 1)
- **THEN** el sistema responde 422 con type `https://api.master-acdc.dev/problems/unknown-product`
- **AND** no se crea ningún pedido y el stock de B sigue en 1

### Requirement: Concurrencia sobre la última unidad
Cuando dos creaciones de pedido concurrentes compitan por las últimas unidades de un producto, el sistema SHALL crear y reservar exactamente un pedido; el otro SHALL ser rechazado con 409 type `https://api.master-acdc.dev/problems/insufficient-stock`, sin crear pedido y sin modificar stock.

#### Scenario: Dos pedidos concurrentes por la última unidad
- **GIVEN** el producto A con stock 1
- **WHEN** dos clientes envían concurrentemente cada uno un pedido con la línea (A, 1)
- **THEN** exactamente un pedido se crea con 201 y reserva la unidad
- **AND** el otro recibe 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`, no se crea ningún segundo pedido y el stock de A queda en 0

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
