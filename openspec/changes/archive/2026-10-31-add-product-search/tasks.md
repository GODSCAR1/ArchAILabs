# Tasks

## 1. Modelo y migración

- [x] 1.1 Añadir categoría, estado y fecha de creación al modelo de producto, y versión únicamente a las entidades JPA mediante `@Version`; verificar invariantes de dominio con `ProductTest` y `CategoryTest` y el mapeo de versión con su prueba de persistencia.
- [x] 1.2 Crear la migración Flyway para categorías y las nuevas columnas/índices, rellenando productos existentes con `Sin categoría`, `ACTIVE`, fecha de migración y versión `0`; verificar con `ProductSearchMigrationIT` que se conservan id, nombre, SKU y precio y que las categorías empiezan en versión `0`.
- [x] 1.3 Actualizar la creación para asignar `Sin categoría`, `ACTIVE` y el instante actual a los productos nuevos; verificar con `CreateProductIT` mediante `JdbcTemplate` que la fila creada conserva categoría `Sin categoría`, status `ACTIVE` y `created_at` no nulo.

## 2. Consulta de aplicación

- [x] 2.1 Definir la entrada del caso de uso, el resumen y los metadatos de página sin exponer entidades; verificar con `ProductSearchServiceTest` que los valores de filtros, página y tamaño se trasladan correctamente.
- [x] 2.2 Añadir el mapeo de solo lectura `@Immutable` del stock dentro de `catalog.infrastructure.adapters.out.persistence` y unirlo a la consulta del catálogo, tratando la ausencia como cero; verificar con `ProductSearchQueryIT` los escenarios `Filtro de disponibilidad sin registro` y `Filtro de disponibilidad con stock positivo` mediante `@DisplayName` exactos.
- [x] 2.3 Implementar una `Specification` por filtro sobre `ProductJpaEntity_` y combinarlas con `Specification.allOf`, sin JPQL concatenado; verificar con `ProductSearchQueryIT` y PostgreSQL/Testcontainers una prueba parametrizada con al menos 8 combinaciones, incluyendo `filtro vacío` y `Los seis filtros combinados`, comprobando los ids devueltos y no solo el número de resultados.
- [x] 2.4 Validar rango de precio, estados, UUID y lista cerrada de campos de orden, añadiendo siempre id como desempate; verificar con `ProductSearchServiceTest` los casos válidos y los escenarios `Rango de precio invertido` y `Campo de orden no permitido` mediante `@DisplayName` exactos.

## 3. API y ensamblado

- [x] 3.1 Exponer `GET /api/products` con parámetros repetibles, normalización de `size` a 100 y respuesta de página de resúmenes; verificar con `ProductControllerTest` los escenarios HTTP de `Consulta sin filtros`, `Tamaño por encima del máximo`, `Filtros de texto categoría precio y estados`, `Filtro de disponibilidad sin registro`, `Ordenación por precio con desempate` y `Estado repetido` mediante `@DisplayName` exactos.
- [x] 3.2 Completar la API con los escenarios `Texto sin distinguir mayúsculas`, `Filtro de disponibilidad con stock positivo`, `Los seis filtros combinados`, `Ordenación por fecha de creación` y `Producto recién creado visible en el catálogo`; verificar cada uno en `ProductSearchIT` con su `@DisplayName` exacto y código HTTP `200`.
- [x] 3.3 Añadir los dos tipos propios de `ProblemDetail` y su traducción HTTP para rango invertido y orden no permitido; verificar con `ProductControllerTest` los escenarios `Rango de precio invertido` y `Campo de orden no permitido`, comprobando `400`, media type RFC 9457 y los types exactos `https://api.master-acdc.dev/problems/invalid-price-range` y `https://api.master-acdc.dev/problems/invalid-sort-field`.
- [x] 3.4 Ensamblar adaptadores exclusivamente desde configuración de infraestructura; verificar con `CatalogArchitectureTest` las reglas `catalogDoesNotDependOnInventory` e `inventoryDoesNotDependOnCatalog`.

## 4. Rendimiento y regresión

- [x] 4.1 Añadir una prueba de consulta con más de 50 productos, `flush()` y `clear()`, `Statistics.clear()` y umbral `<= 2`; verificar con `ProductSearchQueryCountTest` que el número de consultas no depende del número de filas.
- [x] 4.2 Añadir un test de paginación estable con páginas de 7 productos y precios repetidos; verificar con `ProductSearchPaginationTest` que cada producto aparece exactamente una vez al recorrer todas las páginas ordenadas por `price`.
- [x] 4.3 Ejecutar la integración completa del endpoint con PostgreSQL/Testcontainers y verificar `totalElements`, `totalPages`, página vacía y los escenarios `Consulta sin filtros`, `Texto sin distinguir mayúsculas`, `Ordenación por fecha de creación` y `Producto recién creado visible en el catálogo` mediante `@DisplayName` exactos.
- [x] 4.4 Documentar en `docs/adr/0007-buscador-y-concurrencia-del-catalogo.md` la comparación medida de A (Specification + fetch, máximo dos consultas por página) y dejar B (Entity View) como no medida por requerir una dependencia nueva; verificar la decisión y el resultado del umbral `<= 2`.
