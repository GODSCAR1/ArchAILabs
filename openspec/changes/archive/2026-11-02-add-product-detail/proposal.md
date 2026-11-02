# Proposal

## Why

El catálogo permite buscar resúmenes, pero no ofrece una ficha que reúna la información necesaria para decidir una compra: categoría, disponibilidad, variantes, atributos y valoración. Este cambio crea esa lectura como base para el laboratorio S10, manteniendo PostgreSQL y un coste de consultas acotado antes de una futura migración a MongoDB.

## What Changes

- Añadir la persistencia Flyway V6 para variantes, atributos y reseñas, con claves foráneas, restricciones de valoración e índices por producto; reseñas usará `(product_id, created_at DESC, id)` y variantes/atributos sus índices por `product_id`, sin datos semilla.
- Exponer `GET /api/products/{sku}` con la ficha completa del producto, stock como read model de inventory, atributos clave-valor, resumen de valoración y como máximo cinco reseñas recientes.
- Exponer `GET /api/products/{sku}/reviews` con paginación, tamaño por defecto 20, máximo 100 y orden por fecha descendente con desempate por id.
- Devolver `404` con el `type` propio de `ProblemDetail` cuando el SKU no existe.
- Mantener `PUT /api/products/{id}` basado en UUID y evitar ambigüedad entre sus rutas y la consulta por SKU.
- Garantizar que la ficha usa un número fijo de consultas independiente del número de variantes, atributos y reseñas resumidas; la página completa de reseñas queda fuera de ese conteo.
- Mantener sin cambios el buscador existente y no crear APIs de escritura para variantes, atributos o reseñas.

## Capabilities

### New Capabilities

- `product-detail`: Consulta de ficha de producto y página de reseñas asociada.

### Modified Capabilities

- Ninguna. El requisito de `product-search` permanece sin cambios.

## Impact

- Afecta al esquema PostgreSQL/Flyway y a los adaptadores de persistencia del catálogo.
- Añade modelos de lectura, puertos de aplicación, servicio, mappers de infraestructura, DTOs, endpoints y pruebas de integración.
- Reutiliza el read model de stock de inventory sin introducir una dependencia de catalog hacia la API transaccional de inventory.
- Requiere pruebas de número de consultas con PostgreSQL/Testcontainers y pruebas HTTP con los `ProblemDetail` definidos por el proyecto.
- No incorpora MongoDB ni modifica el contrato de creación, edición o búsqueda de productos.

## Non-goals

- Crear o editar variantes, atributos o reseñas mediante API.
- Migrar la persistencia a MongoDB.
- Cambiar filtros, ordenación, paginación o respuesta de `GET /api/products`.
- Introducir reseñas ilimitadas en la respuesta de la ficha; la ficha solo muestra las cinco más recientes.
