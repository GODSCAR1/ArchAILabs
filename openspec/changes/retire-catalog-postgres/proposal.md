# Proposal

## Why

Tras `move-catalog-writes-to-mongodb`, MongoDB es la fuente de verdad por defecto, pero el catálogo aún conserva una ruta de persistencia PostgreSQL, una migración de datos ya innecesaria y tablas de catálogo en PostgreSQL. Retirarlas completa la fase contract de expandir-contraer y reduce el riesgo de que el catálogo vuelva a usar un almacén obsoleto.

## What Changes

- Añadir Flyway V7 para borrar únicamente `reviews`, `product_attributes`, `product_variants`, `categories` y `products`, en orden compatible con sus claves foráneas; conservar `stock` y todas las tablas de ordering.
- Eliminar adaptadores, entidades, repositorios, mappers y especificaciones JPA/PostgreSQL del catálogo; eliminar servicio, checkpoint, job y tests de migración PostgreSQL→Mongo.
- Retirar el selector `catalog.store`; MongoDB será el único almacén del catálogo. Mantener el job de proyección de stock, que seguirá leyendo `stock` con JDBC.
- Reforzar `CatalogMongoArchitectureTest` para que ningún paquete del catálogo dependa de Jakarta Persistence ni Spring Data JPA; `CatalogArchitectureTest` permanece intacto por ser guardián.
- Consolidar las suites duales del catálogo en la suite Mongo y reemplazar el perfil de test PostgreSQL por una extensión JUnit 5 global con MongoDBContainer singleton para los contextos Spring.
- **PERSONA — Fase 0, antes del trabajo del agente**: retirar los cinco guardianes JPA del árbol y de `scripts/protected-tests.txt`, y borrar `src/test/resources/config/application.properties`.
- Conservar `ProductSearchMigrationIT` y `ProductDetailMigrationIT` con objetivos Flyway V5 y V6 respectivamente; no deben aplicar V7.
- Actualizar los deltas de especificación: eliminar selección de almacén, modo PostgreSQL, activación de infraestructura por migración, re-migración periódica, migración reanudable/idempotente, equivalencia entre almacenes y coexistencia. Modificar los requisitos que sobreviven para expresar Mongo como único almacén. Revisar escenarios de `product-search` y `product-detail` que mencionen PostgreSQL y sustituir la métrica de cuatro consultas del detalle por una operación Mongo donde corresponda.
- Documentar la ficha del laboratorio antes/después en `docs/lab-s10.md`, comparando 4 consultas PostgreSQL con 1 comando MongoDB.
- Conservar los contratos HTTP existentes, incluidos status y `ProblemDetail.type`, definidos por los specs actuales de `product-search` y `product-editing`.
- **BREAKING**: la migración V7 elimina de forma irreversible los datos del catálogo que queden en PostgreSQL; MongoDB debe contener el catálogo autoritativo antes de ejecutar el contract.

## Non-goals

- Cambiar inventory u ordering, sus reglas de negocio, persistencia o endpoints; solo resolver la infraestructura que necesitan sus tests de contexto.
- Cambiar o retirar eventos/outbox.
- Modificar `AGENTS.md`, configuración protegida o el paso 8 del laboratorio.
- Cambiar contratos HTTP de catálogo.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `catalog-read-store`: eliminar los requisitos de selección (incluido PostgreSQL por defecto), modo PostgreSQL, activación ligada a migración, re-migración periódica, migración reanudable/idempotente y equivalencia entre almacenes; modificar los requisitos supervivientes para establecer Mongo como almacén único y conservar documento agregado e índices.
- `product-editing`: modificar el requisito de almacén seleccionado para establecer Mongo como único almacén y eliminar los escenarios de coexistencia y modo PostgreSQL.
- `product-search` y `product-detail`: retirar referencias de escenarios a lectura PostgreSQL/equivalencia entre almacenes; en `product-detail`, expresar el coste como una operación de lectura Mongo en lugar de cuatro consultas SQL donde aplique.

## Impact

- `src/main/resources/db/migration`: nueva migración de contract V7 sin tocar tablas de inventory ni ordering.
- `catalog.infrastructure`: eliminación del almacén PostgreSQL/selector y de la migración histórica; conservación de Mongo y del job JDBC de stock.
- Tests de catálogo, `CatalogMongoArchitectureTest` y tests Spring de inventory/ordering; `CatalogArchitectureTest` no se modifica. La Fase 0 humana elimina los guardianes JPA y el perfil de configuración de test indicado.
- `docs/lab-s10.md` y los tests históricos de Flyway V5/V6.
