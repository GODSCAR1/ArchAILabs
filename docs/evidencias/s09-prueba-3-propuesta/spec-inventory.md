# Spec Delta

## MODIFIED Requirements

### Requirement: Reserva atómica de stock
El sistema SHALL permitir reservar, en una sola operación atómica, el stock de un conjunto de productos con sus cantidades. Si todos los productos tienen stock suficiente, el stock reservado SHALL descontarse de cada producto; si alguno no lo tiene, el sistema SHALL rechazar la reserva completa sin modificar ningún stock (todo o nada). El stock resultante de cualquier producto SHALL ser siempre ≥ 0. Si una reserva no puede adquirir temporalmente el bloqueo por concurrencia, el sistema SHALL reintentarlo indefinidamente hasta poder resolver la operación.

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
- **THEN** la reserva se aplica y el stock del producto A queda en 0, nunca negativo

#### Scenario: Reserva en la frontera: cantidad una unidad por encima del stock
- **GIVEN** el producto A con stock 10
- **WHEN** se reserva la línea (A, 11)
- **THEN** se informa insuficiencia de stock y el stock del producto A sigue en 10

#### Scenario: Reserva espera a que termine la contención
- **GIVEN** otra operación mantiene bloqueado el producto A y después libera el bloqueo
- **WHEN** se solicita una reserva HTTP de una unidad para A
- **THEN** la petición espera, termina con HTTP `201`, no devuelve el type `https://api.master-acdc.dev/problems/stock-busy`, y el stock de A se descuenta exactamente una unidad

#### Scenario: Contención seguida de agotamiento de stock
- **GIVEN** otra operación mantiene bloqueado el producto A, libera el bloqueo y deja el stock insuficiente para la reserva
- **WHEN** se solicita una reserva HTTP
- **THEN** la petición termina con HTTP `409` y el type `https://api.master-acdc.dev/problems/insufficient-stock`; el pedido y el stock quedan sin cambios

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
- **THEN** la reserva se aplica y el stock del producto A queda en 0, nunca negativo
