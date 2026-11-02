# Spec Delta

## Purpose

Permite consultar desde el catálogo un resumen agregado del número de productos que pertenecen a cada categoría.

## ADDED Requirements

### Requirement: Conteo de productos agrupado por categoría
El sistema SHALL exponer un resumen de categorías presentes en el catálogo, con el identificador, el nombre y el total de productos de cada una, considerando todos los estados del producto.

#### Scenario: Consulta de conteos por categoría
- **WHEN** el cliente solicita `GET /api/products/category-counts` y hay productos de distintas categorías
- **THEN** el sistema responde `200` con una entrada por cada categoría que tenga productos y el total correcto, incluyendo productos de cualquier estado

#### Scenario: Catálogo vacío
- **WHEN** el cliente solicita `GET /api/products/category-counts` y no hay productos
- **THEN** el sistema responde `200` con una lista vacía

#### Scenario: Categorías sin productos
- **WHEN** el cliente solicita `GET /api/products/category-counts` y una categoría no tiene productos
- **THEN** el sistema responde `200` sin incluir esa categoría
