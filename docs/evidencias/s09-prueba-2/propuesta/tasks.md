# Tasks

## 1. Validación HTTP de la ordenación

- [x] 1.1 Añadir `stock` a la lista blanca de ordenación del listado de productos; verificar con `ProductControllerTest` usando `@DisplayName("Ordenación por stock ascendente")` y `@DisplayName("Ordenación por stock descendente")`.
- [x] 1.2 Conservar el rechazo de campos no permitidos; verificar con `ProductControllerTest` mediante `@DisplayName("Campo de orden no permitido")`, HTTP 400 y el type `https://api.master-acdc.dev/problems/invalid-sort-field`.

## 2. Ordenación en persistencia

- [x] 2.1 Construir la ordenación por stock con `coalesce(stock.available, 0)` mediante `CriteriaBuilder` y conservar id ascendente como último criterio; verificar la implementación en `ProductSearchStockSortIT`.
- [x] 2.2 Crear `ProductSearchStockSortIT` con stock repetido, productos sin stock y recorrido de páginas; verificar los escenarios `Ordenación por stock ascendente`, `Ordenación por stock descendente` y `Producto sin stock ordenado como cero` mediante `@DisplayName` exactos.
- [x] 2.3 Mantener sin cambios los guardianes `ProductSearchPaginationTest` y `ProductSearchQueryCountTest`; verificar que ambos siguen en verde y que el recorrido de páginas devuelve cada producto una sola vez.

## 3. Verificación

- [x] 3.1 Ejecutar `bash ./verify-quality.sh --fast` y verificar que pasa el control de calidad completo.
