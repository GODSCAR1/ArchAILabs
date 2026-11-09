# Spec Delta

## ADDED Requirements

### Requirement: Confirmación interna de pago con factura
El sistema SHALL ofrecer una operación interna para confirmar un pago con pedido e importe total confiable no negativo de hasta dos decimales y 17 dígitos enteros. Solo PENDING SHALL transicionar a PAID, guardando su factura en la misma confirmación atómica. SHALL no exponer un endpoint público de pago ni modificar stock durante la confirmación. El total SHALL no calcularse a partir de precios actuales del catálogo.

#### Scenario: Confirmación interna cambia estado y emite factura
- **WHEN** se confirma internamente un pago de 125.50 para un pedido PENDING
- **THEN** se confirman juntos PAID y una única factura por 125.50; el stock permanece intacto

#### Scenario: Fallo de emisión revierte el pago
- **WHEN** falla la persistencia de la factura durante la confirmación de un pedido PENDING
- **THEN** la operación falla sin confirmar el pago, el pedido permanece PENDING, no queda factura y el stock no cambia

#### Scenario: Importe inválido no confirma pago
- **WHEN** se confirma un pedido PENDING con importe ausente, negativo, con más de dos decimales significativos o superior a 99999999999999999.99
- **THEN** la operación rechaza el importe; pedido, facturas y stock permanecen sin cambios

#### Scenario: Estado incompatible o pedido inexistente no confirma pago
- **WHEN** se confirma internamente un pedido CANCELLED, SHIPPED, REFUNDED, inexistente, o PAID sin factura previa
- **THEN** la operación rechaza la confirmación sin modificar pedidos, facturas ni stock y sin emitir retroactivamente

### Requirement: Reintentos de pago no duplican facturas
Una confirmación repetida de un pedido PAID con el mismo importe y factura existente SHALL devolver la factura original sin cambiar su número ni su fecha. Un importe distinto SHALL rechazarse sin sobrescribirla. Confirmaciones concurrentes SHALL producir una única transición y una factura. La transición SHALL competir de forma atómica con las cancelaciones vigentes.

#### Scenario: Reintento idéntico conserva factura original
- **WHEN** se confirma de nuevo con el mismo importe un pedido PAID facturado
- **THEN** se devuelve la misma factura sin modificar pedido, factura ni stock

#### Scenario: Reintento con importe distinto es rechazado
- **WHEN** se confirma con otro importe un pedido PAID facturado
- **THEN** se rechaza la confirmación sin modificar pedido, factura ni stock

#### Scenario: Confirmaciones simultáneas emiten una factura
- **WHEN** dos confirmaciones internas con el mismo importe compiten por un pedido PENDING
- **THEN** ambas obtienen la misma factura, se confirma una sola transición a PAID y el stock no cambia

#### Scenario: Pago y cancelación concurrentes preservan consistencia
- **WHEN** una confirmación interna compite con una cancelación válida del propietario en POST `/api/orders/{id}/cancellation` sobre un pedido PENDING
- **THEN** si se confirma primero el pago, queda su factura y la cancelación puede responder HTTP 200 pasando PAID a CANCELLED y liberando stock una vez
- **AND** si gana primero la cancelación, responde HTTP 200, la confirmación interna se rechaza, no se emite factura y el rechazo conserva pedido CANCELLED y stock ya liberado
