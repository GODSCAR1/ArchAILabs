# Spec Delta

## MODIFIED Requirements

### Requirement: Creación de pedido válido
El sistema SHALL aceptar POST `/api/orders` con `order:create`, cliente asociado a la cuenta autenticada y una o varias líneas. Si reserva todas las líneas, SHALL responder 201 con `id`, `status` PENDING y `lines`, sin customerId ni Location. SHIPPED sigue sin API de transición en este cambio.

#### Scenario: Pedido creado con stock disponible
- **GIVEN** un cliente asociado a la cuenta CUSTOMER y producto A con stock 10
- **WHEN** envía un pedido con la línea (A, 3) y bearer válido, sin cabecera de cliente
- **THEN** responde HTTP 201 con id, status PENDING y lines, sin customerId ni Location
- **AND** el pedido pertenece al cliente de la cuenta y el stock queda en 7

#### Scenario: Frontera: cantidad igual al stock disponible
- **WHEN** CUSTOMER con cliente asociado pide (A, 10) existiendo stock 10
- **THEN** responde HTTP 201, persiste PENDING y stock queda 0

#### Scenario: Frontera: cantidad una unidad por encima del stock
- **WHEN** CUSTOMER con cliente asociado pide (A, 11) existiendo stock 10
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`; no crea pedido y stock sigue 10

### Requirement: Rechazo de pedido no cancelable
El sistema SHALL rechazar cancelación de un pedido visible PAYING, SHIPPED, CANCELLED o REFUNDED con HTTP 409 y type `https://api.master-acdc.dev/problems/order-not-cancellable`, conservando pedido y stock.

#### Scenario: Cancelación mientras el pago está en curso
- **WHEN** CUSTOMER intenta cancelar su pedido PAYING mientras la autorización está en curso
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; pedido y stock no cambian por la cancelación
- **AND** si la autorización se acepta el pago termina en PAID sin liberar stock

#### Scenario: Frontera: pedido SHIPPED no cancelable
- **WHEN** CUSTOMER cancela su pedido SHIPPED con (A, 1) y stock 9
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; conserva SHIPPED y stock 9

#### Scenario: Pedido ya cancelado
- **WHEN** CUSTOMER cancela su pedido CANCELLED con (A, 3) y stock 10
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; conserva CANCELLED y stock 10

#### Scenario: Pedido reembolsado no cancelable
- **WHEN** CUSTOMER cancela su pedido REFUNDED
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; pedido y stock no cambian

## ADDED Requirements

### Requirement: PAYING es visible en consultas
El sistema SHALL reconocer PAYING en el detalle y como valor exacto del filtro de estado, preservando las reglas de alcance y ordenación existentes.

#### Scenario: Listado de pedidos en pago
- **WHEN** CUSTOMER solicita GET `/api/orders?status=PAYING` con pedidos propios y ajenos en ese estado
- **THEN** responde HTTP 200 con solo los propios; pedidos y stock no cambian
