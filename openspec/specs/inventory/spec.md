# inventory Specification

## Purpose
Gestiona el stock por producto y lo reserva de forma atómica (todo o nada, nunca negativo) para otros módulos mediante su fachada pública.

## Requirements

### Requirement: Reserva atómica de stock
El sistema SHALL permitir reservar, en una sola operación atómica, el stock de un conjunto de productos con sus cantidades. Si todos los productos tienen stock suficiente, el stock reservado SHALL descontarse de cada producto; si alguno no lo tiene, el sistema SHALL rechazar la reserva completa sin modificar ningún stock (todo o nada). El stock resultante de cualquier producto SHALL ser siempre ≥ 0.

#### Scenario: Reserva con stock suficiente en todos los productos
- **GIVEN** el producto A con stock 10 y el producto B con stock 5
- **WHEN** se reserva la línea (A, 3) y la línea (B, 2)
- **THEN** la reserva se aplica y el stock queda A = 7 y B = 3

#### Scenario: Reserva con stock insuficiente en algún producto
- **GIVEN** el producto A con stock 10 y el producto B con stock 1
- **WHEN** se reserva la línea (A, 3) y la línea (B, 2), que no tiene stock suficiente
- **THEN** se informa insuficiencia de stock y el stock de A y B queda sin cambios (10 y 1)

#### Scenario: Reserva en la frontera: cantidad igual al stock disponible
- **GIVEN** el producto A con stock 10
- **WHEN** se reserva la línea (A, 10)
- **THEN** la reserva se aplica y el stock de A queda en 0, nunca negativo

#### Scenario: Reserva en la frontera: cantidad una unidad por encima del stock
- **GIVEN** el producto A con stock 10
- **WHEN** se reserva la línea (A, 11)
- **THEN** se informa insuficiencia de stock y el stock de A sigue en 10

### Requirement: La reserva informa producto desconocido
Cuando una línea de la reserva referencie un producto sin registro de stock, el sistema SHALL informar un resultado propio de producto desconocido, distinto de la insuficiencia de stock, y la reserva completa SHALL rechazarse sin modificar ningún stock. El producto desconocido SHALL ganar siempre a la insuficiencia de stock, sin depender del orden de los productos de la reserva.

#### Scenario: Reserva con producto sin registro de stock
- **GIVEN** el producto A con stock 10 y el producto X sin registro de stock
- **WHEN** se reserva la línea (A, 3) y la línea (X, 1)
- **THEN** se informa producto desconocido y el stock de A sigue en 10

#### Scenario: Reserva con producto desconocido y otro sin stock
- **GIVEN** el producto B con stock 1 y el producto X sin registro de stock
- **WHEN** se reserva la línea (B, 2), que no tiene stock suficiente, y la línea (X, 1)
- **THEN** se informa producto desconocido y el stock de B sigue en 1

### Requirement: Liberación de stock devuelto
El sistema SHALL permitir liberar, en una sola operación atómica, el stock de un conjunto de productos con sus cantidades, devolviendo a cada producto exactamente esas cantidades a su stock disponible. La operación SHALL aceptar cantidades positivas y el stock resultante de cualquier producto SHALL ser la suma del stock previo y la cantidad liberada.

#### Scenario: Liberación de stock de varias líneas
- **GIVEN** el producto A con stock 7 y el producto B con stock 3
- **WHEN** se liberan la línea (A, 3) y la línea (B, 2)
- **THEN** la liberación se aplica y el stock queda A = 10 y B = 5

#### Scenario: Stock liberado vuelve a estar disponible para reservar
- **GIVEN** el producto A con stock 7 y liberada la línea (A, 3)
- **WHEN** se reserva la línea (A, 10)
- **THEN** la reserva se aplica y el stock de A queda en 0, nunca negativo

### Requirement: Reserva directa de unidades de un producto
El sistema SHALL aceptar `POST /api/products/{id}/reservations` con un cuerpo que contenga `units` y SHALL reservar esas unidades únicamente cuando el stock disponible sea suficiente.

#### Scenario: Reserva directa exitosa
- **WHEN** se envía una petición válida para un producto con stock suficiente
- **THEN** el sistema devuelve HTTP 201 con `productId`, `units` reservadas y el stock disponible restante, sin crear ni modificar ningún pedido

#### Scenario: Unidades ausentes
- **WHEN** se envía una petición sin `units`
- **THEN** el sistema devuelve HTTP 400 con el contrato de validación existente, y ningún pedido ni stock queda modificado

#### Scenario: Unidades no positivas
- **WHEN** se envía una petición con `units` menor que 1
- **THEN** el sistema devuelve HTTP 400 con el contrato de validación existente, y ningún pedido ni stock queda modificado

### Requirement: Insuficiencia de stock en una reserva directa
El sistema SHALL rechazar una reserva directa cuando las unidades solicitadas superen el stock disponible y SHALL conservar el stock sin cambios.

#### Scenario: Stock insuficiente
- **WHEN** se solicita una cantidad mayor que el stock disponible del producto
- **THEN** el sistema devuelve HTTP 409 con ProblemDetail type `https://api.master-acdc.dev/problems/insufficient-stock` y propiedad `available` con el stock disponible, sin crear ni modificar ningún pedido ni stock

### Requirement: Producto sin registro de stock
El sistema SHALL distinguir un producto sin registro de stock de una reserva con stock insuficiente.

#### Scenario: Stock inexistente
- **WHEN** se solicita una reserva para un producto que no tiene registro de stock
- **THEN** el sistema devuelve HTTP 404 con ProblemDetail type `https://api.master-acdc.dev/problems/stock-not-found`, sin crear ni modificar ningún pedido ni stock

### Requirement: Tiempo máximo de espera por concurrencia
El sistema SHALL limitar a dos segundos la espera para adquirir el bloqueo necesario para reservar stock y SHALL rechazar la operación si no puede obtenerlo dentro de ese límite.

#### Scenario: Stock ocupado
- **WHEN** otra transacción mantiene bloqueado el stock durante la reserva y el bloqueo no se libera dentro de dos segundos
- **THEN** el sistema devuelve HTTP 503 con cabecera `Retry-After: 1` y ProblemDetail type `https://api.master-acdc.dev/problems/stock-busy`, sin crear ni modificar ningún pedido ni stock

#### Scenario: Reservas concurrentes en venta flash
- **WHEN** 50 reservas simultáneas de una unidad compiten por un stock inicial de 30 unidades
- **THEN** exactamente 30 peticiones devuelven HTTP 201, 20 devuelven HTTP 409 con type `https://api.master-acdc.dev/problems/insufficient-stock`, el stock final es 0 y nunca es negativo

### Requirement: Reserva HTTP autenticada
El sistema SHALL exigir JWT válido en POST `/api/products/{id}/reservations` antes de validar unidades o reservar stock. Los escenarios HTTP de reserva existentes SHALL ejecutarse con esa precondición, conservando resultados, atomicidad y espera máxima. Esta autenticación SHALL no afectar a las operaciones internas de reserva y liberación entre módulos.

#### Scenario: Reserva anónima no llega al inventario
- **WHEN** se solicita POST `/api/products/{id}/reservations` sin JWT válido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin crear pedidos ni modificar stock

#### Scenario: Reserva con JWT conserva contrato de éxito
- **WHEN** se solicita POST `/api/products/{id}/reservations` con JWT válido, units 1 y stock inicial 2
- **THEN** responde HTTP 201 con productId, units 1 y disponible 1; no crea ni modifica pedidos
