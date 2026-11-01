# Proposal

## Why

El catálogo no ofrece una lectura paginada ni filtros combinables, por lo que los clientes no pueden buscar productos de forma útil y estable. Se necesita un contrato de consulta que devuelva resúmenes, limite el coste de las consultas y exponga el stock disponible sin convertir cada fila en una llamada independiente a inventory.

## What Changes

- Añadir `GET /api/products` con filtros opcionales combinables por texto, categoría, rango de precio, estado y disponibilidad.
- Añadir paginación y ordenación limitada a `name`, `price` y `createdAt`, con desempate determinista por id.
- Devolver una página de resúmenes con `id`, `name`, `price`, `stock` y `categoryName`, nunca entidades persistentes.
- Definir errores `400` con `ProblemDetail` y tipos propios para rango de precio inválido y campo de orden no permitido.
- Añadir al modelo persistente de productos categoría, estado, fecha de creación y versión; añadir versión a categorías y migrar los productos existentes.
- Leer el stock mediante un join en la consulta del catálogo, sin invocar la API de inventory una vez por producto.
- Hacer que los productos creados después de la migración tengan categoría, estado y fecha de creación utilizables por la consulta.

## Non-goals

- Editar o eliminar productos o categorías.
- Reservar stock o modificar inventario desde el listado.
- Búsqueda full-text, relevancia o análisis lingüístico.
- Filtrar por SKU.

## Capabilities

### New Capabilities

- `product-search`: consulta paginada de productos con filtros, ordenación, resúmenes y errores de validación del contrato HTTP.

### Modified Capabilities

<!-- No existing capability requirements change. -->

## Impact

- Afecta al controlador HTTP y al caso de uso de consulta del módulo catalog.
- Afecta a las entidades/adaptadores de persistencia de catalog y a las migraciones Flyway de productos y categorías.
- Añade un mapeo de solo lectura del stock dentro de la infraestructura del catálogo, unido en la consulta de búsqueda.
- Requiere pruebas unitarias, MVC e integración con PostgreSQL/Testcontainers, incluida una prueba de número constante de consultas con más de 50 productos.
