# Tasks

## 1. Valoración VIP

- [x] 1.1 Añadir el indicador VIP a `PricingOrder`, actualizar sus llamadores y aplicar el descuento en `OrderPricing` sin cambiar `subtotal`; verificar con `OrderPricingTest` y `PricingTest` los escenarios `@DisplayName("Descuento VIP después del volumen y antes del cupón y los impuestos")`, `@DisplayName("Pedido sin cliente VIP")` y `@DisplayName("Descuento VIP sin cupón")`, además del subtotal observable.
- [x] 1.2 Verificar redondeo y suelo con `OrderPricingTest`: `@DisplayName("Redondeo bancario del descuento VIP")` y `@DisplayName("Cupón superior al subtotal con descuento VIP")`.
- [x] 1.3 Comprobar en `CouponServiceTest` que `checkout` aplica el descuento a un pedido VIP y conserva el marcado de un cupón de un uso; verificar el total y el estado observable del cupón.

## 2. Verificación integrada

- [x] 2.1 Ejecutar `bash ./mvnw spotless:apply` y `bash ./verify-quality.sh --fast`; verificar que los tests, las reglas de arquitectura y PIT de pricing pasan sin reducir el umbral del 93 %.
