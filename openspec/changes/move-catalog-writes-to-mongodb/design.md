# Design

## Context

El change `migrate-catalog-reads-to-mongodb` materializa en MongoDB los documentos del catálogo y permite alimentar ese almacén mientras PostgreSQL sigue activo. Los puertos de lectura y escritura del catálogo son distintos, así que el nuevo selector debe configurar ambos juntos. Inventory conserva la autoridad del stock. Véase `proposal.md` y `specs/product-editing/spec.md` para motivación y contrato.

## Goals / Non-Goals

**Goals:** ofrecer un corte explícito PostgreSQL→Mongo para todos los adaptadores de catálogo, preparar el stock como proyección y hacer que la persona controle el cambio irreversible de default después de validar los guardianes.

**Non-Goals:** mantener doble escritura, sincronizar automáticamente los productos Mongo de vuelta a PostgreSQL, retirar tablas/adaptadores JPA o cambiar las reservas de stock.

## Decisions

- **Un selector para todas las operaciones de catálogo.** `catalog.store=postgres|mongo` selecciona en conjunto los adaptadores de lectura y escritura de `ProductRepository`, `ProductEditingRepository`, `ProductSearchRepository` y `ProductDetailRepository`. En `postgres`, las operaciones permanecen como hoy y la migración puede alimentar Mongo. En `mongo`, MongoDB es la fuente de verdad del catálogo. La infraestructura Mongo se activa si `catalog.store=mongo` o `catalog.migration.enabled=true`. Alternativa descartada: mantener un flag solo lector, que permite separar accidentalmente lectura y escritura.
- **Creación y edición Mongo.** Conservar categoría por defecto `00000000-0000-0000-0000-000000000001` / `Sin categoría`, estado activo, timestamp y versión inicial cero. Usar el índice único SKU; mapear la colisión incluso al guardar al HTTP 409/type actual `about:blank`. En edición, comparar y actualizar condicionalmente por `_id` y `version`, incrementar la versión al confirmar y mantener la distinción entre 404 y conflicto 409 `https://api.master-acdc.dev/problems/concurrent-modification`.
- **Deuda de tipo de error SKU.** SKU duplicado responde hoy con `ProblemDetail.type=about:blank`, aunque `openspec/config.yaml` pide un type por error de negocio. Este change preserva el contrato observable actual; corregir ese type es una decisión funcional separada.
- **Proyección de stock en lotes.** Recorrer documentos de producto Mongo usando keyset por `_id`, formando lotes acotados. Para cada lote consultar PostgreSQL con `product_id in (:ids)` mediante `JdbcTemplate`, indexar resultados por id y asignar cero a ids ausentes; actualizar `availableStock` en bulk. Así las consultas SQL dependen de los lotes, no del número de productos, y no se cargan entidades JPA ni se consulta dentro de un bucle por producto.
- **Límite de arquitectura.** El SQL reside en infraestructura de catalog sin tipos Java de inventory. Se mantiene `CatalogMongoArchitectureTest.catalog_does_not_depend_on_inventory`, además de `catalog_application_depends_only_on_domain`, `mongo_dependencies_stay_in_catalog_infrastructure` y `catalog_mappers_stay_in_adapter_packages`. El retraso hasta el próximo ciclo es aceptable para el indicador de catálogo; reservas siempre consultan inventory/PostgreSQL.
- **Fases de corte y guardias.** Fase A mantiene default `catalog.store=postgres` mientras se implementan y verifican los adaptadores Mongo, el job y las clases de tests Mongo. Fase B corresponde a una persona: actualizar `scripts/protected-tests.txt` y configurar explícitamente en PostgreSQL o retirar los cinco guardianes JPA. El agente se detiene ahí. Solo después Fase C establece Mongo como default y ejecuta verify.
- **Almacén predeterminado en tests Spring.** La persona añade `src/test/resources/config/application.properties` con `catalog.store=postgres` para los `@SpringBootTest` que no declaran almacén y solo necesitan PostgreSQL (inventory, ordering, IT JPA y guardianes JPA). Los tests Mongo fijan `catalog.store=mongo` explícitamente. Esta propiedad de test no debe ocultar el default de producción: `CatalogStoreConfigurationTest` usa `ApplicationContextRunner` sin cargar ese archivo y verifica que la ausencia de `catalog.store` selecciona Mongo. Se retirará cuando PostgreSQL se retire en `retire-catalog-postgres`.
- **Corte de migración manual.** Desplegar con `catalog.store=postgres` y `catalog.migration.enabled=true` hasta que el checkpoint esté `COMPLETADO`; luego cambiar a `catalog.store=mongo` y `catalog.migration.enabled=false`. No se agrega lógica de barrido final automático ni se reactiva la migración periódica después del corte. La copia periódica de stock permanece independiente.
- **Rollback antes de contract.** Volver a `catalog.store=postgres` sin más no expone las altas y ediciones que existan únicamente en Mongo; las lecturas pueden quedar obsoletas y las escrituras posteriores irán a PostgreSQL. Pausar escrituras y reconciliar primero Mongo→PostgreSQL (productos, versiones y SKUs), verificar los datos y recién entonces cambiar el selector. Sin reconciliación, las escrituras Mongo siguen en Mongo pero no son visibles desde PostgreSQL. No hay sincronización automática o doble escritura para facilitar ese rollback.

## Reglas de arquitectura

- `catalog.domain` no depende de Spring, Mongo, JDBC ni JPA; los puertos de salida siguen en domain y sus adaptadores en infrastructure.
- `catalog.application` depende solo de `catalog.domain`; no conoce adaptadores de persistencia ni Mongo/JDBC.
- MongoDB y mappers viven en `catalog.infrastructure.adapters`; se conservan las reglas `mongo_dependencies_stay_in_catalog_infrastructure` y `catalog_mappers_stay_in_adapter_packages`.
- El acceso SQL a stock queda en infraestructura de catálogo sin dependencia de tipos de inventory; se conserva `CatalogMongoArchitectureTest.catalog_does_not_depend_on_inventory`. Catalog no escribe stock ni llama a la API de inventory.
- Beans del job se ensamblan en `infrastructure.config`; la consulta JDBC es parametrizada, solo de lectura y evita JPA/SQL concatenado.

## Risks / Trade-offs

- [Los guardianes JPA arrancan con el nuevo default Mongo] → Fase A conserva default PostgreSQL; la persona adapta o retira los cinco guardianes antes de Fase C.
- [Rollback a PostgreSQL oculta escrituras que solo están en Mongo] → documentar y exigir reconciliación antes de revertir el selector.
- [Error de stock por lote o desfase] → probar lote con productos con y sin fila de stock, y verificar que el número de consultas depende de lotes; dejar las reservas en su fuente de verdad.
- [SKU duplicado conserva `about:blank`] → documentar como deuda explícita y probar que el HTTP/type actual no cambia.
- [La migración periódica sobrescribe cambios Mongo después del corte] → desactivarla tras el checkpoint completo y verificar por configuración `migration.enabled=false`, sin introducir ciclo de vida automático.

## Migration Plan

1. **Fase A:** default PostgreSQL; validar escrituras Mongo mediante `catalog.store=mongo` en tests, y permitir migración PostgreSQL→Mongo con `catalog.store=postgres` y `migration.enabled=true`.
2. **Fase B (persona):** añadir los guardianes Mongo a `scripts/protected-tests.txt` y fijar PostgreSQL explícito o retirar `ProductEditConflictTest`, `ProductEditingIT`, `ProductSearchQueryCountTest`, `ProductSearchPaginationTest` y `ProductDetailQueryCountTest`.
3. **Fase C:** solo tras completar Fase B, establecer Mongo como default, documentar el procedimiento operativo y ejecutar `bash ./verify-quality.sh --fast`.
4. Para el corte operativo, mantener `store=postgres` con migración habilitada hasta checkpoint `COMPLETADO`; luego poner `store=mongo` y `migration.enabled=false`.
5. Para rollback pre-contract, pausar escrituras, reconciliar productos/versiones/SKUs Mongo→PostgreSQL y verificar la reconciliación antes de fijar `store=postgres`. Sin reconciliación, datos escritos en Mongo no aparecerán en PostgreSQL.
6. Mantener tablas, adaptadores JPA y la opción PostgreSQL hasta `retire-catalog-postgres`.

## Open Questions

Ninguna.
