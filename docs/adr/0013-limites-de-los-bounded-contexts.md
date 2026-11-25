# ADR 0013 — Límites de los bounded contexts

- Estado: aceptado (lab S19, 2026-11-24)
- Evidencias: `docs/domain/event-storming.md`, `docs/domain/context-map.md`, `docs/domain/glossary.md`

## Contexto

- El Event Storming del 2026-11-24 dio 72 eventos y 5 pivotes (*Producto publicado*, *Pedido creado*,
  *Pedido confirmado*, *Envío expedido*, *Envío entregado*). El vocabulario cambia de un contexto a otro.
  Hubo tres términos que significaban dos cosas según quién hablara: *reserva* frente a *apartado*,
  *cobro anulado* frente a *reembolso* y *cliente* frente a *cuenta de usuario*.
- Prueba del cambio: buscador, caché del catálogo, refresh tokens y cupones tocaron un solo módulo cada uno.
  El reembolso toca `ordering` e `identity`, pero para negocio pertenece a Devoluciones, Pagos y
  Facturación (`Order.refund` mezcla cobro anulado y reembolso).
- `exports` no tiene ningún evento en el tablero. Notificaciones solo aparece como sistema externo.
- Los hotspots se concentran en el pedido (H1, H4, H5, H7) y en el precio (H8, H9).

## Decisión

1. Existen 10 contextos, con la responsabilidad y los agregados de la tabla de `docs/domain/context-map.md`:
   Pedidos (`ordering`), Precios y promociones (`pricing`), Catálogo (`catalog`), Inventario
   (`inventory`), Identidad y acceso (`identity`), Pagos y riesgo (dentro de `ordering` hasta la
   Sección 35), y Envíos, Devoluciones, Facturación y Atención al cliente, todavía sin módulo.
2. **Core:** Pedidos y Precios y promociones. **Generic:** Identidad, Facturación y la pasarela de Pagos.
   El resto son **supporting**.
3. **Shared kernel:** solo `pricing.domain.model.{Money, OrderPricing, PricingOrder, PricingLine}`, que
   comparten Precios y Pedidos. `Coupon`, `VolumeDiscount` y el resto de `pricing` no forman parte de él.
4. `exports` no es un contexto, sino un detalle técnico. Notificaciones es un adaptador del contexto que avisa.
5. Cada término del glosario pertenece a un solo contexto. Un concepto que parece de dos son dos términos.

**Verificación:** `ContextMapTest` (ArchUnit): `catalog_depends_on_no_other_context`,
`inventory_depends_on_no_other_context`, `pricing_depends_on_no_other_context`,
`identity_only_depends_on_the_ordering_open_host`, `exports_is_isolated` y `shared_kernel_is_limited`.
Además siguen vigentes `ModuleBoundariesTest` (lab S18) y la revisión de PR contra `docs/domain/glossary.md`.

## Consecuencias

- ✅ Cada módulo tiene un dueño de lenguaje, y la Sección 35 parte de fronteras ya probadas.
- ✅ Una dependencia nueva entre contextos rompe el build hasta que se cambie el mapa.
- ❌ El shared kernel acopla las versiones de `pricing` y `ordering`: cambiar `Money` u `OrderPricing` exige
  tocar los dos a la vez.
- ❌ Pagos y riesgo sigue dentro de `ordering`, y la mezcla de cobro anulado y reembolso continúa hasta la
  Sección 35.
- ❌ Los cuatro contextos sin módulo solo existen en la documentación: ningún test los protege todavía.

## Alternativas

- Sacar Pagos a su propio módulo ya: descartada. Es trabajo de la Sección 35 y no cambia ninguna regla de hoy.
- Que `pricing` publique una API en lugar de un shared kernel: descartada por ahora, por coste frente a riesgo (3 tipos compartidos).
- Catálogo como core: descartada. Ninguna funcionalidad del catálogo diferencia a la tienda.
