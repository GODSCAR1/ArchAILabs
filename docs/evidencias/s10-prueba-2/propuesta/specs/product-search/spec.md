# Spec Delta

## MODIFIED Requirements

### Requirement: Filtros combinables de catálogo
El sistema SHALL combinar todos los filtros presentes: `text` busca contenido sin distinguir mayúsculas sobre `name`; `categoryId` exige igualdad; `minPrice` y `maxPrice` son límites inclusivos en COP enteros; `status` puede repetirse y combina sus valores con OR; `inStock=true` conserva solo productos con disponibilidad mayor que cero; y `brand` exige igualdad sin distinguir mayúsculas con el atributo `brand` del producto.

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

#### Scenario: Filtro por marca
- **WHEN** el cliente solicita `GET /api/products?brand=ACME` y hay productos con el atributo `brand` igual a `ACME` y a otra marca
- **THEN** el sistema responde `200` incluyendo únicamente los productos cuya marca coincide con `ACME`

#### Scenario: Filtro por marca sin distinguir mayúsculas
- **WHEN** el cliente solicita `GET /api/products?brand=acme` y hay un producto cuyo atributo `brand` es `ACME`
- **THEN** el sistema responde `200` incluyendo ese producto

#### Scenario: Marca combinada con otros filtros
- **WHEN** el cliente solicita `GET /api/products?brand=ACME&status=ACTIVE&inStock=true`
- **THEN** el sistema responde `200` incluyendo únicamente productos cuya marca es `ACME`, cuyo estado es `ACTIVE` y cuya disponibilidad es mayor que cero
