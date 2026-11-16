# Design

## Context

`OrderPricing` calcula el subtotal con `VolumeDiscount`, aplica el cupón y después impuestos. `Money` redondea cada operación a dos decimales con HALF_EVEN. `PricingOrder` solo contiene líneas y `CouponService` lo pasa al cálculo sin modificarlo.

## Goals / Non-Goals

**Goals:** incorporar la condición VIP al dato de entrada y mantener las reglas existentes de dinero y subtotal observable.

**Non-Goals:** obtener la condición VIP de identidad o integrar la valoración con `ordering`.

## Decisions

- Añadir un booleano explícito a `PricingOrder` y actualizar los llamadores existentes. Una consulta a otro módulo o una decisión basada en el cliente autenticado ampliaría el alcance innecesariamente.
- Aplicar el descuento en `OrderPricing.total` entre `subtotalOf` y `applyCoupon`, calculando el 5 % mediante `Money.times` y restándolo con `Money.minus`. Así se respeta el redondeo existente; multiplicar directamente por 0,95 puede dar un resultado distinto en empates.
- Conservar `subtotal` como subtotal tras volumen, sin aplicar VIP ni cupón, pues ya es una operación pública con ese comportamiento. No introducir otro cálculo paralelo.
- Probar también `CouponService.checkout` con pedido VIP: ya delega en el cálculo y debe conservar el marcado de cupones de un uso.

## Reglas de arquitectura

- `pricing.domain` no depende de Spring ni de infraestructura; `pricing.application` depende de dominio y de sus puertos, no de infraestructura.
- No se añade dependencia entre `pricing` y `ordering`, `identity`, `inventory` o `catalog`. Las reglas arquitectónicas existentes de ArchUnit se mantienen; no se afirma que comprueben todas estas dependencias específicas.

## Risks / Trade-offs

- [Cambio de firma del pedido] → actualizar todos los llamadores y ejecutar las pruebas guardianas; si se rompe su firma o compilación, detenerse y pedir una decisión humana, sin sobrecargas de compatibilidad.
- [Redondeo de importes pequeños] → probar explícitamente un empate HALF_EVEN y la acumulación con cupón fijo.

## Migration Plan

Actualizar consumidores Java de `PricingOrder` en el mismo cambio; no hay migración de datos. La reversión consiste en revertir conjuntamente modelo, cálculo y llamadores.
