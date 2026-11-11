# ordering Specification

## Purpose
Permite a un cliente crear pedidos con líneas de producto y cantidad, reservando stock de forma todo o nada, y define los rechazos de la API con ProblemDetail.

## Requirements

### Requirement: Creación de pedido válido
El sistema SHALL aceptar POST `/api/orders` con `order:create`, cliente asociado a la cuenta autenticada y una o varias líneas. Si reserva todas las líneas, SHALL responder 201 con `id`, `status` PENDING y `lines`, sin customerId ni Location. PAID y SHIPPED siguen sin API de transición en este cambio.

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

### Requirement: Pedido con al menos una línea
El sistema SHALL rechazar la creación de pedido con código 422 y ProblemDetail type `https://api.master-acdc.dev/problems/empty-order` cuando el pedido no lleve ninguna línea. Ante este rechazo, el pedido no se crea y ningún stock queda reservado ni modificado.

#### Scenario: Pedido sin líneas
- **GIVEN** una cuenta CUSTOMER autenticada con cliente asociado
- **WHEN** envía un pedido sin líneas
- **THEN** responde HTTP 422 con type `https://api.master-acdc.dev/problems/empty-order`; no crea pedido ni modifica stock

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
El sistema SHALL comprobar autenticación (401), permiso y cliente asociado (403), cuerpo (422), producto conocido (422) y stock (409), en ese orden para peticiones JSON legibles. Ante rechazo SHALL no crear pedido ni modificar stock.

#### Scenario: Precedencia: error de cabecera antes que cuerpo y stock
- **GIVEN** el nombre histórico del escenario se conserva para trazabilidad; la cabecera de identidad ya no es parte del contrato y la precedencia corresponde al permiso
- **WHEN** CATALOG_MANAGER envía POST `/api/orders` con cantidad cero y otra línea sin stock
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; pedidos y stock no cambian

#### Scenario: Precedencia: error de cuerpo antes que stock
- **GIVEN** CUSTOMER con cliente asociado, A con stock 10 y B con stock 1
- **WHEN** pide (A, 0) y (B, 2)
- **THEN** responde HTTP 422 con type `https://api.master-acdc.dev/problems/invalid-quantity`; no crea pedido y stocks siguen 10 y 1

#### Scenario: Precedencia: producto desconocido antes que stock insuficiente
- **GIVEN** CUSTOMER con cliente asociado, B con stock 1 y X desconocido
- **WHEN** pide (B, 2) y (X, 1)
- **THEN** responde HTTP 422 con type `https://api.master-acdc.dev/problems/unknown-product`; no crea pedido y stock de B sigue 1

### Requirement: Concurrencia sobre la última unidad
Cuando dos creaciones de pedido concurrentes compitan por las últimas unidades de un producto, el sistema SHALL crear y reservar exactamente un pedido; el otro SHALL ser rechazado con 409 type `https://api.master-acdc.dev/problems/insufficient-stock`, sin crear pedido y sin modificar stock.

#### Scenario: Dos pedidos concurrentes por la última unidad
- **GIVEN** el producto A con stock 1
- **WHEN** dos clientes envían concurrentemente cada uno un pedido con la línea (A, 1)
- **THEN** exactamente un pedido se crea con 201 y reserva la unidad
- **AND** el otro recibe 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`, no se crea ningún segundo pedido y el stock de A queda en 0

### Requirement: Cancelación de pedido cancelable
El sistema SHALL exigir `order:cancel-own` en POST `/api/orders/{id}/cancellation` y limitar siempre la consulta y la modificación al cliente de la cuenta, incluso si también posee `order:read-any`. PENDING y PAID SHALL pasar a CANCELLED, responder 200 con id/status/lines sin customerId y liberar todas sus líneas una sola vez.

#### Scenario: Pedido PENDING cancelado libera stock de todas sus líneas
- **GIVEN** pedido propio PENDING con (A, 3), (B, 2), stocks 7 y 8
- **WHEN** CUSTOMER propietario solicita cancelación con bearer
- **THEN** responde HTTP 200 sin customerId, persiste CANCELLED y ambos stocks quedan en 10

#### Scenario: Frontera: pedido PAID cancelable
- **GIVEN** pedido propio PAID con (A, 1), stock 9
- **WHEN** CUSTOMER propietario solicita cancelación
- **THEN** responde HTTP 200 con CANCELLED y stock 10

### Requirement: Rechazo de pedido no cancelable
El sistema SHALL rechazar cancelación de un pedido visible SHIPPED, CANCELLED o REFUNDED con HTTP 409 y type `https://api.master-acdc.dev/problems/order-not-cancellable`, conservando pedido y stock.

#### Scenario: Frontera: pedido SHIPPED no cancelable
- **WHEN** CUSTOMER cancela su pedido SHIPPED con (A, 1) y stock 9
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; conserva SHIPPED y stock 9

#### Scenario: Pedido ya cancelado
- **WHEN** CUSTOMER cancela su pedido CANCELLED con (A, 3) y stock 10
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; conserva CANCELLED y stock 10

#### Scenario: Pedido reembolsado no cancelable
- **WHEN** CUSTOMER cancela su pedido REFUNDED
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`; pedido y stock no cambian

### Requirement: Cancelación de pedido inexistente o ajeno
Con permiso de cancelación, el sistema SHALL responder 404 con type `https://api.master-acdc.dev/problems/order-not-found` para id inválido, inexistente o fuera del alcance del solicitante, sin distinguir los casos. SHALL no modificar pedido ni stock.

#### Scenario: Pedido inexistente
- **WHEN** CUSTOMER solicita cancelación de un UUID inexistente con bearer válido
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedidos y stock no cambian

#### Scenario: Cancelación con id de pedido que no es UUID
- **WHEN** CUSTOMER solicita cancelación de `no-uuid`
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedidos y stock no cambian

#### Scenario: Pedido de otro cliente
- **WHEN** CUSTOMER solicita cancelación del pedido PENDING de otro cliente
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; conserva el pedido PENDING y su stock

#### Scenario: Usuario multirrol no puede cancelar un pedido ajeno
- **GIVEN** una cuenta CUSTOMER+SUPPORT con order:cancel-own y order:read-any y un pedido PENDING de otro cliente
- **WHEN** solicita POST `/api/orders/{id}/cancellation` para ese pedido ajeno con bearer válido
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; el pedido permanece PENDING y ningún stock se modifica

### Requirement: Precedencia de errores de cancelación
El sistema SHALL comprobar autenticación (401), permiso (403), existencia dentro del alcance (404) y estado (409), en ese orden, sin modificar pedido ni stock en rechazos.

#### Scenario: Precedencia: cabecera antes que existencia
- **GIVEN** el nombre histórico del escenario se conserva para trazabilidad; ahora se verifica permiso en vez de cabecera de cliente
- **WHEN** SUPPORT solicita cancelar un UUID inexistente
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; pedidos y stock no cambian

#### Scenario: Precedencia: propiedad antes que estado
- **WHEN** CUSTOMER solicita cancelar un pedido ajeno SHIPPED
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedido SHIPPED y stock no cambian

### Requirement: Concurrencia de cancelaciones del mismo pedido
Cuando dos cancelaciones autorizadas del mismo pedido compitan, el sistema SHALL confirmar exactamente una y liberar stock una vez; la otra SHALL recibir 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable` sin segunda modificación.

#### Scenario: Dos cancelaciones concurrentes del mismo pedido
- **GIVEN** pedido propio PENDING con (A, 1) y stock 9
- **WHEN** CUSTOMER propietario solicita dos cancelaciones concurrentes con bearer válido
- **THEN** una responde HTTP 200, persiste CANCELLED y stock 10; la otra HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`, sin segunda liberación

### Requirement: Listado de pedidos por estado
GET `/api/orders?status=<estado>` SHALL exigir `order:read-own` o `order:read-any` y filtrar en la consulta por cliente de la cuenta, salvo `order:read-any`. SHALL responder 200 con resúmenes id/customerName/lineCount/totalUnits de los pedidos visibles coincidentes. Los requisitos de estado obligatorio, válido y exacto siguen vigentes tras autorizar.

#### Scenario: Pedidos PAID listados con nombre y métricas
- **WHEN** CUSTOMER consulta PAID con pedidos propios y ajenos PAID
- **THEN** responde HTTP 200 solo con propios, id/customerName/lineCount/totalUnits correctos, sin cambiar pedidos ni stock

#### Scenario: Lectura global de pedidos por estado
- **WHEN** SUPPORT o ADMIN sin cliente asociado consulta PAID
- **THEN** responde HTTP 200 con todos los pedidos PAID y sus métricas, sin cambiar pedidos ni stock

#### Scenario: Cliente sin pedidos visibles
- **WHEN** CUSTOMER consulta PAID y solo existen pedidos ajenos PAID
- **THEN** responde HTTP 200 con [], sin cambiar pedidos ni stock

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

### Requirement: Identidad de cliente derivada de la cuenta
El cliente SHALL obtenerse de la cuenta identificada por sub en cada petición de pedidos, sin cache. La API SHALL no aceptar customerId como selector ni exponerlo en respuestas. Una cuenta sin cliente SHALL no crear pedidos; sin lectura global SHALL obtener lista vacía o 404 al consultar un objeto. Cabeceras antiguas SHALL no alterar identidad.

#### Scenario: Cuenta sin cliente no crea pedido
- **WHEN** una cuenta CUSTOMER sin cliente asociado solicita POST `/api/orders` con stock suficiente
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; no crea pedido ni modifica stock

#### Scenario: Cabecera antigua no suplanta identidad
- **WHEN** CUSTOMER con cliente propio crea un pedido válido enviando X-Customer-Id de otro cliente
- **THEN** responde HTTP 201, pertenece al cliente del bearer y no expone customerId; únicamente reserva sus líneas

#### Scenario: Cambio de asociación se observa con el mismo token
- **WHEN** se cambia la asociación de cliente de una cuenta entre dos GET `/api/orders?status=PAID` usando el mismo bearer válido
- **THEN** ambas respuestas son HTTP 200 y la segunda contiene únicamente pedidos de la asociación vigente; no cambia pedido ni stock

#### Scenario: Cliente ausente no abre el alcance
- **WHEN** CUSTOMER sin cliente asociado consulta GET `/api/orders?status=PAID` y después GET `/api/orders/{id}` existente
- **THEN** recibe HTTP 200 con [] y HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`, respectivamente; pedidos y stock no cambian

### Requirement: Detalle de pedido limitado por propiedad
GET `/api/orders/{id}` SHALL exigir permiso de lectura antes de consultar y devolver HTTP 200 con id/status/lines sin customerId para objetos visibles. La consulta SHALL limitar por cliente salvo `order:read-any`. Id inválido, inexistente o ajeno SHALL producir el mismo 404 con type `https://api.master-acdc.dev/problems/order-not-found`.

#### Scenario: Detalle propio y lectura global
- **WHEN** CUSTOMER consulta su pedido o SUPPORT/ADMIN consulta cualquier pedido existente
- **THEN** responde HTTP 200 con id/status/lines sin customerId, sin modificar pedidos ni stock

#### Scenario: Detalle ajeno o inexistente
- **WHEN** CUSTOMER consulta un pedido ajeno, inexistente o un id no UUID
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedidos y stock no cambian

### Requirement: Reembolso de pedido pagado o enviado
POST `/api/orders/{id}/refund` SHALL exigir `order:refund` y pasar de PAID o SHIPPED a REFUNDED de forma atómica, respondiendo HTTP 200 con id/status/lines sin customerId. SHALL no liberar ni modificar stock. Objeto inexistente SHALL responder 404; cualquier otro estado SHALL responder 409.

#### Scenario: Reembolso desde PAID o SHIPPED
- **WHEN** SUPPORT o ADMIN reembolsa un pedido PAID o SHIPPED
- **THEN** responde HTTP 200, persiste REFUNDED y conserva stock

#### Scenario: Reembolso rechazado por estado
- **WHEN** SUPPORT o ADMIN reembolsa un pedido PENDING, CANCELLED o REFUNDED
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-refundable`; pedido y stock no cambian

#### Scenario: Reembolso de pedido inexistente
- **WHEN** SUPPORT o ADMIN reembolsa un id inexistente o no UUID
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedidos y stock no cambian

#### Scenario: Reembolsos concurrentes no repiten transición
- **WHEN** dos reembolsos autorizados compiten por el mismo pedido PAID
- **THEN** uno responde HTTP 200 y otro HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-refundable`; queda REFUNDED y stock intacto

#### Scenario: Cancelación y reembolso concurrentes
- **WHEN** una cancelación del propietario y un reembolso de SUPPORT compiten por el mismo pedido PAID
- **THEN** exactamente uno responde HTTP 200; el perdedor recibe HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable` o `https://api.master-acdc.dev/problems/order-not-refundable` según operación
- **AND** si gana cancelación queda CANCELLED y libera stock una vez; si gana reembolso queda REFUNDED sin liberar stock; el rechazo no modifica nada

### Requirement: Ordenación restringida del listado de pedidos
GET `/api/orders` SHALL aceptar `sort` solo como un campo de la lista `id`, `customerName`, `lineCount`, `totalUnits`, opcionalmente seguido de ASC o DESC. Sin dirección SHALL usar ASC y sin sort SHALL usar `id ASC`. SHALL añadir id como criterio final estable, sin duplicarlo si ya es el campo solicitado. SHALL mantener permisos, alcance, métricas y clientes sin nombre.

#### Scenario: Ordenación permitida conserva unidades descendentes
- **GIVEN** un bearer SUPPORT vigente y pedidos PAID con distintos totales y empates
- **WHEN** solicita GET `/api/orders?status=PAID&sort=totalUnits%20DESC`
- **THEN** responde HTTP 200 con totales descendentes y empates ordenados por id ASC
- **AND** pedidos y stock no cambian

#### Scenario: Campos permitidos y orden predeterminado
- **GIVEN** un bearer con permiso de lectura y pedidos visibles
- **WHEN** solicita GET `/api/orders?status=PAID` sin sort o con cada campo permitido y direcciones ASC/DESC
- **THEN** responde HTTP 200 con el orden solicitado, usando ASC si se omite dirección e id ASC si se omite sort
- **AND** pedidos y stock no cambian

### Requirement: Rechazo de ordenación arbitraria sin efectos
El sistema SHALL rechazar campos, direcciones y fragmentos de ordenación fuera del contrato con HTTP 400 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-order-sort`, antes de ejecutar la consulta de listado. SHALL no interpretar entrada de usuario como instrucciones y SHALL conservar pedidos y stock.

#### Scenario: Ordenación inyectada se rechaza y conserva pedidos
- **GIVEN** un bearer SUPPORT vigente y pedidos PAID persistidos
- **WHEN** solicita GET `/api/orders?status=PAID` con `sort=totalUnits; drop table orders--`
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-order-sort`
- **AND** todos los pedidos siguen existiendo y conservan datos, el stock no cambia y un listado válido posterior responde HTTP 200

#### Scenario: Campo dirección o fragmento no permitido
- **GIVEN** un bearer con permiso de lectura
- **WHEN** solicita GET `/api/orders?status=PAID` con sort vacío, campo desconocido, dirección distinta de ASC/DESC o fragmentos adicionales
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-order-sort`
- **AND** pedidos y stock no cambian
