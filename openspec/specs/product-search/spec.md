# product-search Specification

## Purpose
Permite consultar el catálogo de forma paginada, filtrable y determinista, devolviendo únicamente los datos resumidos necesarios para mostrar productos y su disponibilidad.

## Requirements

### Requirement: Consulta paginada de productos
El sistema SHALL exponer `GET /api/products` y devolver una página de resúmenes con `id`, `name`, `price`, `stock` y `categoryName`, junto con `page`, `size`, `totalElements` y `totalPages`; nunca SHALL devolver la entidad completa.

#### Scenario: Consulta sin filtros
- **WHEN** el cliente solicita `GET /api/products` sin parámetros
- **THEN** el sistema responde `200` con la primera página (`page=0`, `size=20`) y los metadatos de paginación

#### Scenario: Producto recién creado visible en el catálogo
- **WHEN** un producto creado mediante `POST /api/products` se consulta con `GET /api/products?status=ACTIVE`
- **THEN** el sistema responde `200` incluyendo ese producto con `categoryName` igual a `Sin categoría`, como producto de estado `ACTIVE`

#### Scenario: Tamaño por encima del máximo
- **WHEN** el cliente solicita `GET /api/products?size=1000`
- **THEN** el sistema responde `200` con como máximo 100 resultados en la página y `size=100`

### Requirement: Filtros combinables de catálogo
El sistema SHALL combinar todos los filtros presentes: `text` busca contenido sin distinguir mayúsculas sobre `name`; `categoryId` exige igualdad; `minPrice` y `maxPrice` son límites inclusivos en COP enteros; `status` puede repetirse y combina sus valores con OR; e `inStock=true` conserva solo productos con disponibilidad mayor que cero.

#### Scenario: Filtros de texto categoría precio y estados
- **WHEN** el cliente solicita `GET /api/products?text=cafe&categoryId=00000000-0000-0000-0000-000000000001&minPrice=1000&maxPrice=5000&status=ACTIVE&status=DRAFT`
- **THEN** el sistema responde `200` con productos que cumplen texto, categoría, rango y cualquiera de los dos estados

#### Scenario: Texto sin distinguir mayúsculas
- **WHEN** el cliente solicita `GET /api/products?text=LAP` y existe un producto llamado `Laptop`
- **THEN** el sistema responde `200` incluyendo `Laptop`

#### Scenario: Filtro de disponibilidad con stock positivo
- **WHEN** el cliente solicita `GET /api/products?inStock=true` y existe un producto con stock disponible mayor que cero
- **THEN** el sistema responde `200` incluyendo ese producto

#### Scenario: Los seis filtros combinados
- **WHEN** el cliente solicita `GET /api/products?text=LAP&categoryId=00000000-0000-0000-0000-000000000001&minPrice=1000&maxPrice=5000&status=ACTIVE&inStock=true`
- **THEN** el sistema responde `200` incluyendo solo productos que cumplen simultáneamente texto, categoría, precio mínimo, precio máximo, estado y disponibilidad

#### Scenario: Filtro de disponibilidad sin registro
- **WHEN** el cliente solicita `GET /api/products?inStock=true` y un producto no tiene registro de disponibilidad
- **THEN** el sistema responde `200` excluyendo ese producto, tratándolo como `stock=0`

### Requirement: Validación del rango de precio
El sistema SHALL rechazar un rango cuando `minPrice` sea mayor que `maxPrice`.

#### Scenario: Rango de precio invertido
- **WHEN** el cliente solicita `GET /api/products?minPrice=5001&maxPrice=5000`
- **THEN** el sistema responde `400` con `ProblemDetail` cuyo `type` es `https://api.master-acdc.dev/problems/invalid-price-range` y no modifica productos ni stock

### Requirement: Ordenación permitida y estable
El sistema SHALL aceptar solo `name`, `price` y `createdAt` en `sort`; cualquier orden SHALL desempatar por `id`, y un campo no permitido SHALL ser rechazado.

#### Scenario: Ordenación por precio con desempate
- **WHEN** el cliente solicita `GET /api/products?sort=price,desc` con productos del mismo precio
- **THEN** el sistema responde `200` ordenando por precio descendente y después por id de forma determinista

#### Scenario: Campo de orden no permitido
- **WHEN** el cliente solicita `GET /api/products?sort=sku,asc`
- **THEN** el sistema responde `400` con `ProblemDetail` cuyo `type` es `https://api.master-acdc.dev/problems/invalid-sort-field` y no modifica productos ni stock

#### Scenario: Ordenación por fecha de creación
- **WHEN** el cliente solicita `GET /api/products?sort=createdAt,desc`
- **THEN** el sistema responde `200` ordenando por fecha de creación descendente y después por id de forma determinista

### Requirement: Valores de paginación y estado
El sistema SHALL usar `page=0` y `size=20` por defecto, limitar `size` a 100 sin convertir tamaños mayores en error, y aceptar como estados únicamente `DRAFT`, `ACTIVE` y `DISCONTINUED`.

#### Scenario: Estado repetido
- **WHEN** el cliente solicita `GET /api/products?status=ACTIVE&status=DISCONTINUED`
- **THEN** el sistema responde `200` incluyendo productos de cualquiera de esos estados
