# Tasks

## 1. Modelo documental e infraestructura Mongo

- [x] 1.1 Crear documentos de `CatalogProduct`, `CategoryRef`, `Variant`, `RatingSummary` y `ReviewSnippet`, el documento de reseña completa y sus mappers estáticos en infraestructura. Tests de mapper con `ProductCatalogMigrationIT` cubren `Ficha agregada disponible` y `Arrays acotados`: `latestReviews` queda en cinco; un producto con más de 100 variantes no se materializa, se informa como rechazado con SKU y no detiene el resto del lote.
- [x] 1.2 Implementar inicializador idempotente de `$jsonSchema`, índice único de `sku` e índice compuesto `{category._id, price}`, con auto-indexación desactivada y sin índices de atributos. `CatalogMongoStructureIT` verifica con Testcontainers los escenarios `Arranque con estructura válida`, `Documento inválido rechazado` e `Índices reproducibles` mediante dos inicializaciones.
- [x] 1.3 Añadir adaptador Mongo de detalle y reseñas, incluido el mapeo de ausencia a la excepción HTTP existente. `ProductDetailIT` y `ProductReviewQueryIT` verifican `Producto inexistente en ambos modos` con `@DisplayName` exacto, HTTP 404 y `ProblemDetail.type=https://api.master-acdc.dev/problems/product-not-found`.
- [x] 1.4 Añadir adaptador Mongo de búsqueda con los filtros, whitelist de orden, desempate por UUID y paginación del puerto. `ProductSearchIT` verifica `Consulta sin filtros`, `Filtros de texto categoría precio y estados`, `Los seis filtros combinados`, `Ordenación por precio con desempate`, `Estado repetido`, más texto literal con caracteres especiales y orden por nombre equivalente a PostgreSQL con mayúsculas, minúsculas y acentos.

## 2. Migración por lotes

- [x] 2.1 Implementar lectura PostgreSQL por UUID con keyset y construcción del documento agregado, copiando categoría, `availableStock`, variantes, atributos, rating `HALF_UP` y hasta cinco reseñas recientes. `ProductCatalogMigrationIT` verifica `Copia de stock y categoría` y `Ficha agregada disponible`.
- [x] 2.2 Implementar upsert por SKU, upsert de reseñas completas y checkpoint `{lastProcessedId, estado=EN_CURSO|COMPLETADO}` persistente. `ProductCatalogMigrationIT` verifica `Reanudación tras interrupción`, `Ejecución repetida`, `Informe de migración` y `Ciclo siguiente tras un barrido completo`; reanudar continúa tras el último UUID confirmado, completar fuerza que el siguiente ciclo empiece desde el inicio y ninguna consulta usa OFFSET.
- [x] 2.3 Implementar informe con productos, variantes, reseñas y rechazos identificados por SKU, además del job periódico. `CatalogMigrationJobTest` verifica `Migración habilitada` y `Migración desactivada`; cada ciclo habilitado recorre todos los productos, sin detección de modificados.

## 3. Ensamblaje y aislamiento arquitectónico

- [x] 3.1 Condicionar solo los adaptadores lectores a `catalog.read-store` (`postgres` por defecto, `mongo` alternativo), mantener incondicionales los beans de escritura y activar inicializador, validador y job si `catalog.read-store=mongo` O `catalog.migration.enabled=true`. `CatalogReadStoreConfigurationTest` verifica el escenario `Infraestructura Mongo activada por lectura o migración`, selección exclusiva y activación con cada propiedad.
- [x] 3.2 Configurar Mongo con `spring.data.mongodb.auto-index-creation=false`. Los tests existentes que no configuran ninguna de las dos propiedades no deben requerir Mongo: el driver conecta perezosamente y no hay auto-indexación al arrancar. Verificarlo con `MasterAcdcApplicationIT`; los contextos Mongo de integración usan URI de Testcontainers.
- [x] 3.3 Añadir reglas ArchUnit para aislar Spring Data Mongo en infraestructura, conservar `application -> domain`, impedir dependencias de `catalog` hacia `inventory` y exigir mappers dedicados. `CatalogArchitectureTest` verifica las reglas de design.md.

## 4. Suite dual y paridad

- [ ] 4.1 Reorganizar únicamente `ProductSearchIT`, `ProductDetailIT` y `ProductReviewQueryIT` en suites HTTP comunes y subclases `postgres`/`mongo`. Exponer `afterWrite()` en la base y ejecutarlo en la subclase Mongo para migrar tras cada escritura. Conservar `@DisplayName` exactos, incluido `Respuesta HTTP equivalente a PostgreSQL` y `Producto recién creado visible en el catálogo`; este último demuestra que la escritura PostgreSQL aún no aparece en Mongo hasta el hook de migración. No mover ni modificar ningún test guardián de `scripts/protected-tests.txt`, incluidos `ProductSearchQueryCountTest`, `ProductSearchPaginationTest` y `ProductDetailQueryCountTest`.
- [ ] 4.2 Preparar datos en PostgreSQL y ejecutar migración antes de los tests Mongo. Verificar `Lecturas por defecto en PostgreSQL`, `Lecturas activadas en MongoDB` y `Reseñas completas paginadas` con Testcontainers, además de `Producto inexistente en ambos modos` en las dos subclases.
- [ ] 4.3 Añadir pruebas de paridad de recuentos, 200 SKU aleatorios, búsquedas e idempotencia. Como `@ConditionalOnProperty` instala solo un lector, `CatalogMigrationParityIT` instancia explícitamente el adaptador JPA con `EntityManager` y el adaptador Mongo con `MongoTemplate`, comparando `ProductDetail` y `ProductSearchPage` del dominio. Verifica `Recuentos equivalentes`, `Fichas equivalentes` y `Buscador equivalente`; usar regex con `Pattern.quote` para el texto literal (p. ej. `a.b` y `(`), collation/criterio equivalente a PostgreSQL para name con mayúsculas/minúsculas/acentos y exactamente dos operaciones para count más página.
- [ ] 4.4 Medir el coste de ficha en `ProductDetailQueryCountTest` mediante un `CommandListener` del driver registrado en el test, contando comandos `find` y `aggregate`; verificar exactamente 1 comando al cargar la ficha con 60 reseñas, 60 variantes y 60 atributos, y que las reseñas completas se consultan desde la colección separada.

## 5. Verificación del cambio

- [ ] 5.1 Ejecutar integración con ambos contenedores y corregir divergencias de payload, orden, paginación, recuento o `ProblemDetail`; confirmar que todos los escenarios del delta tienen una tarea y un test con `@DisplayName` idéntico.
- [ ] 5.2 Ejecutar `bash ./verify-quality.sh --fast` y confirmar specs, Spotless, Checkstyle y tests en verde sin desactivar guardianes ni modificar la zona protegida.
