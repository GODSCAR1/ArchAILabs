# Tasks

## 1. Filtro de búsqueda por marca

- [x] 1.1 Añadir `brand` al criterio y al mapeo HTTP y aplicar igualdad completa sin distinguir mayúsculas sobre `attributes.brand` tanto en la página como en el conteo; verificar con pruebas cuyo `@DisplayName` coincida exactamente con «Filtro por marca», «Filtro por marca sin distinguir mayúsculas» y «Marca combinada con otros filtros».
- [x] 1.2 Declarar el índice ascendente de `attributes.brand` y comprobarlo, incluida la inicialización repetida, en `CatalogMongoStructureIT`; verificar con pruebas cuyo `@DisplayName` coincida exactamente con «Arranque con estructura válida» e «Índices reproducibles».

## 2. Verificación integrada

- [x] 2.1 Ejecutar `bash ./verify-quality.sh --fast` y confirmar que specs, formato, Checkstyle y tests terminan correctamente.
