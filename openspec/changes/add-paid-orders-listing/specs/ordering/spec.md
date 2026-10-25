# Spec Delta

## ADDED Requirements

### Requirement: Listado de pedidos por estado
El sistema SHALL exponer `GET /api/orders?status=<estado>` y responder 200 con una lista de resúmenes que contenga `id`, `customerName`, `lineCount` y `totalUnits` para cada pedido coincidente.

#### Scenario: Pedidos PAID listados con nombre y métricas
- **WHEN** se solicita `GET /api/orders?status=PAID` y existen pedidos PAID con cliente y líneas
- **THEN** el sistema responde 200 con un resumen por pedido que incluye su `id`, el nombre del cliente en `customerName`, el número de líneas en `lineCount` y la suma de cantidades en `totalUnits`
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: El filtro limita el estado devuelto
El sistema SHALL incluir únicamente pedidos cuyo estado sea exactamente el valor solicitado en `status`.

#### Scenario: Un pedido PENDING no aparece al solicitar PAID
- **WHEN** se solicita `GET /api/orders?status=PAID` y existen pedidos PAID y PENDING
- **THEN** el sistema responde 200 y la lista contiene los pedidos PAID pero no el pedido PENDING
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: Listado vacío para un estado sin pedidos
El sistema SHALL responder 200 con una lista JSON vacía cuando no existan pedidos del estado solicitado.

#### Scenario: Sin pedidos PAID
- **WHEN** se solicita `GET /api/orders?status=PAID` y no existe ningún pedido PAID
- **THEN** el sistema responde 200 con `[]`
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: Estado válido y comparación exacta en el filtro
El sistema SHALL aceptar únicamente valores de `status` que coincidan exactamente con los nombres de los estados de pedido y SHALL rechazar los demás con 400 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-order-status`.

#### Scenario: Status inválido
- **WHEN** se solicita `GET /api/orders?status=FOO`
- **THEN** el sistema responde 400 con ProblemDetail type `https://api.master-acdc.dev/problems/invalid-order-status`
- **AND** no se modifica ningún pedido ni ningún stock

#### Scenario: Status en minúsculas
- **WHEN** se solicita `GET /api/orders?status=paid`
- **THEN** el sistema responde 400 con ProblemDetail type `https://api.master-acdc.dev/problems/invalid-order-status`
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: Status obligatorio en el filtro
El sistema SHALL rechazar la ausencia del parámetro `status` con 400 y ProblemDetail type `https://api.master-acdc.dev/problems/missing-order-status`, en lugar de listar todos los pedidos.

#### Scenario: Sin parámetro status
- **WHEN** se solicita `GET /api/orders` sin parámetro `status`
- **THEN** el sistema responde 400 con ProblemDetail type `https://api.master-acdc.dev/problems/missing-order-status`
- **AND** no se modifica ningún pedido ni ningún stock

### Requirement: Cliente no encontrado en la consulta
El sistema SHALL conservar el pedido en el listado y representar `customerName` como `null` cuando no exista información de nombre para su `customerId`.

#### Scenario: Pedido cuyo cliente no existe
- **WHEN** se solicita `GET /api/orders?status=PAID` y un pedido PAID referencia un cliente sin nombre registrado
- **THEN** el sistema responde 200, incluye el pedido y establece `customerName` en `null`
- **AND** no se modifica ningún pedido ni ningún stock
