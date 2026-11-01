# Spec Delta

## ADDED Requirements

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
