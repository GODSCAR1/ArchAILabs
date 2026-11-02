# Tasks

## 1. Fase A — Agente: preparar Mongo con PostgreSQL por defecto

- [x] 1.1 Implementar `catalog.store=postgres|mongo` para seleccionar juntos los cuatro adaptadores de lectura/escritura y activar infraestructura Mongo si `store=mongo` o `migration.enabled=true`; mantener default PostgreSQL. Verificar `CatalogStoreConfigurationTest` con `@DisplayName("Modo PostgreSQL durante coexistencia")`, `@DisplayName("Migración Mongo habilitada en modo PostgreSQL")` y `@DisplayName("Modo Mongo con migración desactivada")`.
- [x] 1.2 Implementar creación Mongo en `ProductRepository`, preservando categoría por defecto, estado, timestamp y versión inicial; verificar test HTTP nuevo `@DisplayName("Producto creado")` y colisión del índice con `@DisplayName("SKU duplicado")`, HTTP 409/type `about:blank` y documento existente sin cambios.
- [x] 1.3 Implementar edición Mongo con comparación condicional de id+versión y mapeo de conflicto/no encontrado; verificar una clase nueva de conflicto Mongo con `@DisplayName("Versión del cliente obsoleta")`, `@DisplayName("Conflicto detectado al guardar")` y `@DisplayName("Producto inexistente")`, comprobando status, type y ausencia de cambios rechazados.
- [x] 1.4 Añadir tests de integración HTTP Mongo nuevos que sustituyan funcionalmente `ProductEditingIT`; verificar `CatalogProductEditingMongoIT` con `@DisplayName("Producto creado")`, `@DisplayName("Edición válida del producto")`, `@DisplayName("Nombre o precio inválido")` y `@DisplayName("Versión ausente")`, manteniendo el contrato HTTP y que el producto no cambia ante rechazo.
- [x] 1.5 Crear equivalente Mongo en clase nueva para `ProductSearchQueryCountTest`; verificar exactamente dos comandos Mongo por búsqueda con el test de conteo dedicado.
- [x] 1.6 Crear equivalente Mongo en clase nueva para `ProductSearchPaginationTest`; verificar orden determinista, empate resuelto por id y conjunto con más de 50 productos.
- [x] 1.7 Crear equivalente Mongo en clase nueva para `ProductDetailQueryCountTest`; verificar exactamente un comando Mongo por ficha.
- [x] 1.8 Implementar job de stock: keyset por `_id` de productos Mongo, consulta JDBC agrupada `product_id in (:ids)` y actualización bulk, asignando cero a productos sin fila; verificar `@DisplayName("Actualización periódica de stock")` y `@DisplayName("Producto sin fila de stock")` y comprobar que consultas SQL = lotes, no productos individuales.
- [x] 1.9 Probar que la opción `catalog.store=postgres` mantiene lecturas y escrituras PostgreSQL mientras `migration.enabled=true` permite alimentar Mongo; comprobar además `CatalogMongoArchitectureTest.catalog_does_not_depend_on_inventory`.
- [x] 1.10 Ejecutar `bash ./verify-quality.sh --fast` con PostgreSQL por defecto y todos los guardianes JPA originales intactos; corregir hasta que pase antes de ceder a la persona.

## 2. Fase B — PERSONA: adaptar los guardianes JPA (el agente se detiene aquí)

- [ ] 2.1 **PERSONA — no marcar como completada por el agente.** Añadir las clases guardianas Mongo nuevas a `scripts/protected-tests.txt` y en los cinco tests JPA `ProductEditConflictTest`, `ProductEditingIT`, `ProductSearchQueryCountTest`, `ProductSearchPaginationTest` y `ProductDetailQueryCountTest` fijar `catalog.store=postgres` explícitamente o retirarlos. Verificar con `bash ./verify-quality.sh --fast` y confirmar que los guardianes JPA que se conserven arrancan en PostgreSQL.

## 3. Fase C — Agente: cambiar el default después de la intervención humana

- [ ] 3.1 Solo después de completar 2.1, cambiar el default a `catalog.store=mongo`; verificar `CatalogStoreConfigurationTest` con `@DisplayName("Almacén Mongo por defecto")` (solo Fase C) y `@DisplayName("Modo PostgreSQL durante coexistencia")`, confirmando que `catalog.store=postgres` selecciona lecturas y escrituras PostgreSQL.
- [ ] 3.2 Actualizar `docs/catalog-model.md` y `docs/adr/0009-catalogo-en-mongodb.md`: Mongo como fuente de verdad del catálogo en modo mongo, job de stock por lotes y desfase, y SKU duplicado `about:blank` como deuda de contrato; verificar coherencia con este design y con los escenarios del spec.
- [ ] 3.3 Documentar el corte operativo (mantener `store=postgres` y `migration.enabled=true` hasta checkpoint `COMPLETADO`; luego `store=mongo` y `migration.enabled=false`) y el rollback con reconciliación previa; verificar test de configuración de las propiedades sin añadir lógica de ciclo de vida para barrido final.
- [ ] 3.4 Ejecutar `bash ./verify-quality.sh --fast` después del cambio de default y la adaptación humana; confirmar suite dual en ambos valores de `catalog.store`.

## Workflow follow-up

- Después de revisión humana, actualizar la lista de guardianes protegidos y archivar los changes únicamente mediante el proceso humano habitual.
