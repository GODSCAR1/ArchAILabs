# Proposal

## Why

El buscador actual permite filtrar productos por texto, categoría, precio, estado y stock, pero no permite localizar directamente un producto mediante su SKU. Añadir un filtro de coincidencia sobre el SKU normalizado cubre búsquedas operativas de un producto concreto sin convertir el SKU en una búsqueda parcial.

## What Changes

- Añadir el parámetro opcional `sku` al buscador de productos.
- Normalizar el parámetro mediante las reglas del objeto de valor `Sku`: espacios exteriores, mayúsculas y formato permitido.
- Aplicar una coincidencia exacta del SKU normalizado cuando el parámetro esté presente.
- Combinar el filtro SKU con los demás filtros mediante AND.
- Rechazar con HTTP 400 los SKU con formato inválido.
- Cubrir el contrato en pruebas web y de persistencia.

## Capabilities

### New Capabilities

- Ninguna.

### Modified Capabilities

- `product-search`: ampliar el requisito `Filtros combinables de catálogo` con el filtro por SKU normalizado. Esto revierte el non-goal "Filtrar por SKU" de `add-product-search`.

## Impact

- Afecta al contrato HTTP `GET /api/products` y al criterio de búsqueda del catálogo.
- Afecta al dominio de búsqueda y al adaptador de persistencia mediante el nuevo filtro.
- No requiere migración de base de datos, dependencia nueva ni cambio de formato de respuesta.

## Non-goals

- No se añade búsqueda parcial ni una lista de SKUs.
- No se cambia la validación o unicidad existente de los SKU.
- No se modifica la paginación, ordenación ni los demás filtros.
