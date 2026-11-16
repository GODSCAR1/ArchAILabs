# Spec Delta

## Purpose

Definir la valoración de pedidos con descuentos acumulables y tratamiento monetario predecible.

## ADDED Requirements

### Requirement: Descuento de cliente VIP
El sistema SHALL aceptar en el pedido a valorar un indicador de cliente VIP y aplicar un descuento del 5 % sobre el subtotal después del descuento por volumen, antes del cupón y de los impuestos.

#### Scenario: Descuento VIP después del volumen y antes del cupón y los impuestos
- **WHEN** un pedido VIP tiene un subtotal tras volumen de 90,00, un cupón fijo válido de 10,00 y un impuesto del 19 %
- **THEN** el descuento VIP es 4,50 y el total es 89,84

#### Scenario: Pedido sin cliente VIP
- **WHEN** un pedido no VIP tiene un subtotal tras volumen de 90,00, un cupón fijo válido de 10,00 y un impuesto del 19 %
- **THEN** no se aplica descuento VIP y el total es 95,20

#### Scenario: Descuento VIP sin cupón
- **WHEN** un pedido VIP tiene un subtotal tras volumen de 100,00, sin cupón y sin impuestos
- **THEN** el total es 95,00

### Requirement: Redondeo y suelo del descuento VIP
El sistema SHALL redondear el importe del descuento VIP a dos decimales con redondeo HALF_EVEN y SHALL mantener el total en cero como mínimo.

#### Scenario: Redondeo bancario del descuento VIP
- **WHEN** un pedido VIP tiene un subtotal tras volumen de 0,50, sin cupón y sin impuestos
- **THEN** el descuento VIP es 0,02 y el total es 0,48

#### Scenario: Cupón superior al subtotal con descuento VIP
- **WHEN** un pedido VIP tiene un subtotal tras volumen de 100,00 y un cupón fijo válido de 100,00
- **THEN** el total es 0,00 incluso si se aplican impuestos
