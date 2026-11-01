# Spec Delta

## MODIFIED Requirements

### Requirement: Filtros combinables de catálogo

El sistema SHALL combinar todos los filtros presentes: `text` busca contenido sin distinguir mayúsculas sobre `name`; `categoryId` exige igualdad; `minPrice` y `maxPrice` son límites inclusivos en COP enteros; `status` puede repetirse y combina sus valores con OR; `inStock=true` conserva solo productos con disponibilidad mayor que cero; y `sku` exige igualdad sobre el SKU normalizado.

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

#### Scenario: Filtro por SKU existente
- **WHEN** el cliente solicita `GET /api/products?sku=CAF-500` y existe un producto con SKU `CAF-500`
- **THEN** el sistema responde `200` incluyendo solo el producto con ese SKU

#### Scenario: SKU inexistente
- **WHEN** el cliente solicita `GET /api/products?sku=SKU-UNKNOWN`
- **THEN** el sistema responde `200` con una página vacía y `totalElements=0`

#### Scenario: SKU normalizado
- **WHEN** el cliente solicita `GET /api/products?sku=%20caf-500%20` y existe un producto con SKU `CAF-500`
- **THEN** el sistema responde `200` incluyendo el producto con SKU `CAF-500`

#### Scenario: SKU con formato inválido
- **WHEN** el cliente solicita `GET /api/products?sku=CA%2F500`
- **THEN** el sistema responde `400` con un `ProblemDetail` cuyo `type` es `https://api.master-acdc.dev/problems/invalid-search-parameter` y no modifica productos ni stock

#### Scenario: SKU combinado con otros filtros
- **WHEN** el cliente solicita `GET /api/products?sku=CAF-500&status=ACTIVE`
- **THEN** el sistema responde `200` incluyendo solo productos que cumplen simultáneamente el SKU normalizado y el estado
