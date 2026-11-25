# Mapa de contextos

Lo vigila `ModuleBoundariesTest` (lab S18). Una dependencia entre módulos que no esté en esta tabla rompe
el build. Para añadirla hay dos caminos: si es legítima, primero se cambia este mapa (decisión humana), y si
no lo es, se corrige el código.

## Paquete publicado de cada módulo

| Módulo | Publica | Qué es |
|---|---|---|
| `catalog` | `catalog.api` | `CatalogPrices` (precios para un pedido) y `CatalogStockProjection` (proyección del stock tras confirmar) |
| `inventory` | `inventory.api` | `InventoryFacade` (reservar y liberar stock) |
| `ordering` | `ordering.api` | `CustomerRegistrationFacade` (alta del cliente) y `CurrentOrderActor` (lo implementa identity) |
| `pricing` | `pricing.domain.model` | Núcleo compartido sin fachada: `OrderPricing`, `PricingOrder`, `PricingLine`, `Money` |
| `identity` | nada | Nadie depende de identity |
| `exports` | nada | Módulo aislado |

## Relaciones (aguas arriba → aguas abajo)

| Cliente (downstream) | Proveedor (upstream) | Patrón | Desde | Para qué |
|---|---|---|---|---|
| `ordering` | `catalog` | Cliente-proveedor con ACL | `ordering.infrastructure.adapters.out.catalog`, `config` | Precio de las líneas (`OrderAmountCalculator`) y proyección del stock en el relay de confirmación |
| `ordering` | `inventory` | Cliente-proveedor con ACL | `ordering.infrastructure.adapters.out.inventory`, `config` | Reservar stock al crear un pedido y liberarlo al cancelarlo (`StockReserver`, `OrderCancelledListener`) |
| `ordering` | `pricing` | Núcleo compartido | `ordering.infrastructure.adapters.out.catalog`, `config` | Calcular el importe del pedido |
| `identity` | `ordering` | Puerto publicado (ordering define `CurrentOrderActor` e identity lo implementa) | `identity.infrastructure.adapters.out.ordering`, `config` | Alta del cliente al registrarse y actor actual de un pedido |

Reglas que se derivan de la tabla:

- Sin ciclos entre módulos (`modules_are_free_of_cycles`).
- Un módulo solo usa el paquete publicado de otro, y solo desde `infrastructure`
  (`modules_only_use_the_published_api_of_other_modules`). El núcleo (`domain`, `application`) nunca ve
  otro módulo: pide lo que necesita a un puerto propio de `domain/port`.

## Deuda conocida (congelada en `src/test/resources/archunit_store/`)

- **`inventory.api` publica tipos de su dominio.** `InventoryFacade` recibe `StockLine` y devuelve
  `ReservationResult`, ambos de `inventory.domain.model`. Por eso los adaptadores de ordering dependen del
  dominio de inventory (10 violaciones congeladas, las mismas que congeló el lab S12). Corrección: tipos
  propios en `inventory.api` (`StockLineRequest`, `ReservationOutcome`) y traducir dentro de inventory.

## Comprobación contra el código (lab S18, paso 5)

Las dependencias reales entre módulos (imports de `src/main`) coinciden con la tabla: identity → ordering.api
(5 imports), ordering → catalog.api (5), ordering → inventory.api (3), ordering → pricing.domain.model (4) y
ordering → inventory.domain.model (2: la deuda de arriba). No aparece ninguna dependencia que falte en el mapa.
