# Lab S20

## Diferencias entre el Event Storming y el código

1. **Administrador:** el rol `ADMIN` existe en el código y puede gestionar cuentas de usuario y descargar exportaciones, pero el actor no aparece en `docs/domain/event-storming.md`. Fuentes: `identity/domain/model/Role.java`, `identity/infrastructure/adapters/in/web/AdminUserManagementController.java` y `exports/infrastructure/adapters/in/web/AdminExportController.java`.

## Dependencias reales entre módulos

Origen → destino indica el sentido de la dependencia del código, no la flecha upstream → downstream
del mapa. Rutas relativas a `src/main/java/com/codeja/master_acdc/`. Se agrupan imports del mismo
tipo y se incluyen configuración, SQL y eventos; la presencia de un import no implica por sí sola
una llamada en ejecución.

| Origen → destino | Tipo o mecanismo concreto | Ficheros |
|---|---|---|
| ordering → catalog | `CatalogPrices`, `CatalogPrices.ProductPrice`: consulta de precios | `ordering/infrastructure/adapters/out/catalog/CatalogOrderAmountCalculator.java`; `ordering/infrastructure/config/OrderingConfiguration.java` |
| ordering → catalog | `CatalogStockProjection`: el relay llama a `synchronize` | `ordering/infrastructure/adapters/out/persistence/OrderConfirmationRelay.java`; `ordering/infrastructure/config/ConfirmationRelayConfiguration.java` |
| ordering → inventory | `InventoryFacade.reserve` y `StockLine` de `inventory.domain.model` | `ordering/infrastructure/adapters/out/inventory/StockReserverAdapter.java`; `ordering/infrastructure/config/OrderingConfiguration.java` |
| ordering → inventory | `OrderCancelled` se publica como evento Spring y lo consume un listener **de ordering**, que llama a `InventoryFacade.release` con `StockLine` | `ordering/infrastructure/adapters/out/events/OrderEventsAdapter.java`; `ordering/infrastructure/adapters/out/inventory/OrderCancelledListener.java` |
| ordering → pricing | Shared kernel: `OrderPricing`, `PricingOrder`, `PricingLine` | `ordering/infrastructure/adapters/out/catalog/CatalogOrderAmountCalculator.java`; `ordering/infrastructure/config/OrderingConfiguration.java` |
| identity → ordering | `CustomerRegistrationFacade`: alta síncrona de cliente | `identity/infrastructure/adapters/out/ordering/CustomerCreatorAdapter.java`; `identity/infrastructure/config/IdentityConfiguration.java` |
| identity → ordering | `CurrentOrderActor`: identidad implementa el puerto y mapea su resultado | `identity/infrastructure/adapters/out/ordering/CurrentOrderActorAdapter.java`; `identity/infrastructure/adapters/out/persistence/OrderActorPersistenceMapper.java`; `identity/infrastructure/config/IdentityConfiguration.java` |
| catalog → inventory | SQL directo sobre la tabla `stock` de Inventario | `catalog/infrastructure/adapters/out/mongo/CatalogStockProjectionJob.java` |

`OrderConfirmed` se escribe en el outbox de Pedidos y su relay llama al puerto de Catálogo: no
hay un consumidor de ese evento en Catálogo. `ProductChanged` se publica y consume dentro de
Catálogo, no cruza módulos. No se encontraron imports entre módulos de `exports` ni hacia
`identity`; `pricing` e `inventory` tampoco importan otros de los seis módulos.

## Diferencias con la tabla de relaciones del mapa

| Diferencia | Evidencia (rutas relativas a `src/main/java/com/codeja/master_acdc/`) | Clasificación | Decisión |
|---|---|---|---|
| Fila 6: la proyección no consume `Pedido confirmado` en Catálogo; el relay de Pedidos invoca `CatalogStockProjection`. Ya registrada como deuda «fila 6 al revés». | `ordering/infrastructure/adapters/out/events/OrderEventsAdapter.java`; `ordering/infrastructure/adapters/out/persistence/OrderConfirmationRelay.java` | dependencia que no debería existir | Sin cambio: deuda ya catalogada (ADR 0014 §4). Se corrige en la Sección 39. |
| Fila 2: el puerto de Inventario filtra `StockLine` de su dominio al consumidor. Ya registrada como deuda de `inventory.api`. | `ordering/infrastructure/adapters/out/inventory/StockReserverAdapter.java`; `ordering/infrastructure/adapters/out/inventory/OrderCancelledListener.java` | dependencia que no debería existir | Sin cambio: deuda ya registrada (lab S12, congelada en `archunit_store/`). |
| Fila 2: la liberación se dispara por `OrderCancelled`, pero el listener está en Pedidos y llama síncronamente a `InventoryFacade.release`: es un efecto no catalogado en las excepciones del punto 4 del ADR 0014. El ADR también describe esta cadena como integración por evento; queda pendiente resolver esa tensión. | `ordering/infrastructure/adapters/out/events/OrderEventsAdapter.java`; `ordering/infrastructure/adapters/out/inventory/OrderCancelledListener.java` | dependencia que no debería existir | ADR 0016: `InventoryFacade.release` pasa a excepción catalogada (compensación de `reserve`). Se corrige el mapa (fila 2). Revisión en la Sección 39. |
| Fila 6b: Catálogo accede directamente a la tabla `stock` de Inventario. Ya registrada como deuda y excepción en el mapa. | `catalog/infrastructure/adapters/out/mongo/CatalogStockProjectionJob.java` | dependencia que no debería existir | Sin cambio: deuda ya catalogada (ADR 0014 §4, `TableOwnershipTest`). Se corrige en la Sección 39. |
| Fila 4: Identidad llama a `CustomerRegistrationFacade.register` para crear un cliente; es un efecto síncrono, no un evento entre contextos como dispone el ADR 0014. La fila documenta el puerto, pero no esta diferencia de mecanismo; no está en la lista de deuda del mapa. | `identity/infrastructure/adapters/out/ordering/CustomerCreatorAdapter.java` | dependencia que no debería existir | ADR 0016: `CustomerRegistrationFacade.register` pasa a excepción catalogada (el alta de cuenta y de cliente fallan juntas). Se corrige el mapa (fila 4). Revisión en la Sección 39. |

## Alcance de los diagramas de checkout

Las vistas parten de un pedido existente. En el primer intento se consultan precios y se calcula el
subtotal; en un reintento con importe ya fijado se omiten esas dos llamadas. La autorización
aceptada se registra como `PAID` y `OrderConfirmed` se añade al outbox en la misma transacción;
el relay programado lo procesa después y solicita la proyección. En el rechazo se conserva el
importe y se vuelve a `PENDING`, sin confirmar ni añadir ese evento al outbox.

No se ha confirmado una captura de cobro independiente (paso 15 del tablero), ni un consumidor
de `OrderConfirmed` en Catálogo, ni una invitación explícita a reintentar. El código devuelve
una excepción de pago rechazado. Las policies de riesgo, facturación y envío posteriores a la
confirmación tampoco están implementadas en este flujo.

## Validación y exportación (paso 3)

| Comando | Resultado |
|---|---|
| `validate` | Sin errores |
| `inspect -severity error,warning` | Sin errores ni avisos, tras tres correcciones (abajo) |
| `export -format mermaid` | 5 ficheros en `docs/architecture/diagrams/`, uno por vista |

Lo que señaló `inspect` y cómo se resolvió:

| Inspección | Resolución |
|---|---|
| `workspace.scope`: el workspace no tenía alcance | `configuration { scope softwaresystem }` |
| `model.softwaresystem.documentation`: el sistema no tenía documentación | `!docs README.md` importa el README de arquitectura |
| `model.softwaresystem.decisions`: el sistema no tenía decisiones | Ignorada con motivo en el DSL: los ADR viven en `docs/adr/` con formato propio (no adr-tools), `!adrs` no admite `../adr` y no se duplican a mano. El README enlaza el índice |

Las relaciones se definen solo a nivel de componente; las de contenedor las deduce Structurizr. Solo
las relaciones `Futuro` quedan a nivel de contenedor, porque aún no tienen componente.

## Loop protegido (paso 5)

| Capa | Qué se añadió |
|---|---|
| Guide | `AGENTS.md`: sección «Arquitectura documentada» (mapa del sistema, objetivos de calidad, regla de actualizar `workspace.dsl` en el mismo PR) y excepciones del ADR 0014 ampliadas con el ADR 0016 |
| Verify | `verify-quality.sh --full`: `validate` de Structurizr, solo si la rama toca `docs/architecture/` |
| Verify (zona protegida) | Sin cambios: `src/test/java/com/codeja/master_acdc/architecture/` y `docs/adr/` ya estaban en `protect-verify.sh` desde los labs S18 y S19. El paquete real es `com.codeja.master_acdc`, no el `dev.codeja.shop` del enunciado |
| Solve | Columna «Decisión» de la tabla de diferencias: tres deudas ya catalogadas sin cambio y dos efectos síncronos nuevos resueltos con el ADR 0016 y la corrección del mapa (filas 2 y 4) |
