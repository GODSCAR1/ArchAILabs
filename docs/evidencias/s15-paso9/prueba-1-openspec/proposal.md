# Proposal

## Why

El cálculo de precios necesita reconocer a los clientes VIP y concederles un descuento adicional del 5 % con reglas predecibles junto con los descuentos existentes.

## What Changes

- El pedido valorado indica si su cliente es VIP; el descuento se calcula después del descuento por volumen y antes del cupón y los impuestos.
- Los descuentos se acumulan, se mantiene el suelo de cero y los pedidos no VIP conservan el cálculo actual.
- **BREAKING**: incorporar el indicador al pedido de pricing puede requerir actualizar sus constructores y llamadores Java.

## Capabilities

### New Capabilities

- `pricing`: valoración de pedidos con descuento VIP acumulable.

### Modified Capabilities

Ninguna.

## Impact

Modelo de dominio y pruebas del módulo `pricing`, incluidos sus llamadores internos; sin nuevas dependencias. El umbral PIT de pricing permanece en 93 %.

## Non-goals

- Integración con el checkout de `ordering`, API HTTP, persistencia o administración de clientes VIP.
- Cambios a las reglas existentes de volumen, cupón, impuestos o redondeo.
