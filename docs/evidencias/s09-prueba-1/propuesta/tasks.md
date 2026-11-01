# Tasks

## 1. Criterio y entrada web

- [x] 1.1 Añadir el SKU opcional al criterio de búsqueda y mapearlo mediante el objeto de valor `Sku`, verificándolo con pruebas unitarias del adaptador nombradas `should_map_normalized_sku_when_parameter_has_spaces_and_lowercase` y `should_reject_invalid_sku_when_parameter_has_invalid_format`.
- [x] 1.2 Mantener el comportamiento sin SKU cubierto por el escenario existente `Consulta sin filtros`, sin añadir un escenario duplicado.

## 2. Persistencia y contrato HTTP

- [x] 2.1 Añadir el filtro de igualdad sobre el SKU normalizado a la consulta tipada y verificar el escenario HTTP `Filtro por SKU existente`.
- [x] 2.2 Añadir los escenarios HTTP `SKU inexistente`, `SKU normalizado` y `SKU con formato inválido`, verificando respectivamente página vacía, normalización y `400` con el `type` `https://api.master-acdc.dev/problems/invalid-search-parameter`.
- [x] 2.3 Añadir el escenario HTTP `SKU combinado con otros filtros` y un caso con `sku` al test parametrizado de combinaciones de `ProductSearchQueryIT`, comprobando los IDs devueltos.
- [x] 2.4 Mantener sin cambios `ProductSearchQueryCountTest` y `ProductSearchPaginationTest`, verificando que ambas guardianas siguen en verde.
- [x] 2.5 Ejecutar `bash ./verify-quality.sh --fast` y confirmar que las pruebas, Spotless y Checkstyle pasan.

## 3. Trazabilidad de escenarios

- [x] 3.1 Usar `@DisplayName` exactamente `Filtro por SKU existente`, `SKU inexistente`, `SKU normalizado`, `SKU con formato inválido` y `SKU combinado con otros filtros` en las pruebas HTTP o de integración correspondientes.

## Workflow follow-up

- Revisar la propuesta y los artefactos antes de iniciar la implementación.
- Aplicar el cambio con `/opsx-apply` cuando esté aprobado.
