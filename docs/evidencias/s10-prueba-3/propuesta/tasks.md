# Tasks

## 1. Conteo de dominio y persistencia

- [x] 1.1 Añadir el modelo de lectura y los puertos de entrada/salida para conteos por categoría; verificar con pruebas unitarias del caso de uso y `@DisplayName(Consulta de conteos por categoría)`.
- [x] 1.2 Implementar la agregación tipada MongoDB y mapear sus resultados; verificar con test de integración MongoDB que compruebe totales por categoría, todos los estados, catálogo vacío y categoría sin productos.

## 2. Exposición HTTP

- [x] 2.1 Añadir el mapper web, el endpoint y su ensamblado en configuración; verificar con prueba MVC `@DisplayName(Consulta de conteos por categoría)` y `@DisplayName(Catálogo vacío)`.
- [x] 2.2 Ejecutar `bash ./verify-quality.sh --fast` y verificar que specs, Spotless, Checkstyle y tests pasan.
