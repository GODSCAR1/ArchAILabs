# ADR 0014 — Integración entre contextos: consultas por puertos, efectos por eventos

- Estado: aceptado (lab S19, 2026-11-24)
- Evidencias: `docs/domain/event-storming.md` (tablero frente a código), `docs/domain/context-map.md`
- Relacionados: ADR 0012 (hexagonal), ADR 0013 (límites), ADR 0015 (reserva síncrona)

## Contexto

- Hay una flecha en los dos sentidos entre Catálogo y Pedidos. Catálogo da los precios (consulta), pero la
  disponibilidad la empuja `ordering`, llamando a `catalog.api.CatalogStockProjection` desde su relay:
  Pedidos decide cuándo cambia el Catálogo.
- `CatalogStockProjectionJob` (catalog) lee la tabla `stock` de Inventario por SQL. Es una dependencia que no
  sale en los imports y que `ModuleBoundariesTest` no ve.
- La cancelación ya se integra por evento: `OrderCancelled` → `OrderCancelledListener` → `InventoryFacade.release`.
- La reserva al crear el pedido es síncrona porque el negocio exige que un pedido no nazca sin stock (ADR 0015).
- En la Sección 39 los contextos pasarán a comunicarse con eventos entre servicios.

## Decisión

1. **Consultas síncronas:** solo a través del paquete publicado del contexto dueño (`<modulo>.api`, o el
   shared kernel de `pricing`), y solo desde `infrastructure/adapters/out/<modulo>` o `config` del consumidor.
2. **Efectos en otro contexto:** por eventos de dominio que publica el contexto donde ocurre el cambio. El
   contexto afectado reacciona al evento. Ningún contexto manda una orden a otro para cambiar su estado.
3. **Prohibido** acceder a tablas, colecciones, entidades JPA, documentos Mongo o repositorios de otro contexto.
4. Excepciones catalogadas (solo estas):
   - `InventoryFacade.reserve`: efecto síncrono por decisión de negocio (ADR 0015).
   - `CatalogStockProjection` llamado desde `ordering`: deuda, pasa a evento en la Sección 39.
   - Lectura de la tabla `stock` desde `catalog`: deuda, misma corrección.

**Verificación:** `ModuleBoundariesTest.modules_only_use_the_published_api_of_other_modules` (punto 1),
`ContextMapTest.cross_context_calls_only_use_catalogued_ports` (lista cerrada de puertos, puntos 1 y 4),
`ContextMapTest.no_module_touches_another_modules_persistence` (punto 3, tipos) y `TableOwnershipTest`
(punto 3, SQL: cada tabla tiene un módulo dueño, y la única excepción es `stock` desde `catalog`).

## Consecuencias

- ✅ El acoplamiento entre contextos queda en una lista cerrada que se revisa en cada PR.
- ✅ Llegar a la Sección 39 es sustituir tres excepciones conocidas, no buscar dependencias ocultas.
- ❌ Los efectos por evento son eventualmente consistentes: la disponibilidad del catálogo va por detrás del
  pedido (hoy, hasta el intervalo del relay).
- ❌ Cada puerto nuevo entre contextos obliga a editar la lista de `ContextMapTest` y este mapa, lo que
  añade fricción a propósito.
- ❌ `TableOwnershipTest` busca nombres de tabla en el código fuente: el SQL dinámico se le escapa y un
  nombre de tabla en un comentario da un falso positivo.

## Alternativas

- Todo síncrono por fachadas: descartada. Ata la disponibilidad de un contexto a la del otro y empuja hacia ciclos.
- Base de datos compartida con vistas por contexto: descartada. Esconde el acoplamiento en el esquema.
- Todo por eventos, incluida la reserva: descartada. El pedido podría nacer sin stock (ADR 0015).
