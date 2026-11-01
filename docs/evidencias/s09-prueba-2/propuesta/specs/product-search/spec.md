# Spec Delta

## MODIFIED Requirements

### Requirement: Ordenación permitida y estable
El sistema SHALL aceptar solo `name`, `price`, `createdAt` y `stock` en `sort`; cualquier orden SHALL desempatar por `id`, y un campo no permitido SHALL ser rechazado.

#### Scenario: Ordenación por precio con desempate
- **WHEN** el cliente solicita `GET /api/products?sort=price,desc` con productos del mismo precio
- **THEN** el sistema responde `200` ordenando por precio descendente y después por id de forma determinista

#### Scenario: Campo de orden no permitido
- **WHEN** el cliente solicita `GET /api/products?sort=sku,asc`
- **THEN** el sistema responde `400` con `ProblemDetail` cuyo `type` es `https://api.master-acdc.dev/problems/invalid-sort-field` y no modifica productos ni stock

#### Scenario: Ordenación por fecha de creación
- **WHEN** el cliente solicita `GET /api/products?sort=createdAt,desc`
- **THEN** el sistema responde `200` ordenando por fecha de creación descendente y después por id de forma determinista

#### Scenario: Ordenación por stock ascendente
- **WHEN** el cliente solicita `GET /api/products?sort=stock,asc`
- **THEN** el sistema responde `200` ordenando por stock de menor a mayor y después por id ascendente de forma determinista

#### Scenario: Ordenación por stock descendente
- **WHEN** el cliente solicita `GET /api/products?sort=stock,desc`
- **THEN** el sistema responde `200` ordenando por stock de mayor a menor y después por id ascendente de forma determinista

#### Scenario: Producto sin stock ordenado como cero
- **WHEN** el cliente solicita `GET /api/products?sort=stock,asc` y un producto no tiene registro de stock
- **THEN** el sistema responde `200`, trata ese producto como `stock=0`, lo ordena junto a los productos con stock cero y desempata por id ascendente
