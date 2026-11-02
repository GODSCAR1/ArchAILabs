# Proposal

## Why

El catálogo no ofrece una forma de conocer cuántos productos corresponden a cada categoría. Esta consulta permite a clientes del catálogo mostrar un resumen de distribución por categoría sin descargar ni recorrer todas las páginas de productos.

## What Changes

- Exponer una operación de lectura del catálogo que devuelva cada categoría presente y su número total de productos.
- Calcular el resumen en MongoDB en una operación agregada, incluyendo todos los estados de producto y omitiendo categorías sin productos.

## Capabilities

### New Capabilities
- `category-product-counts`: Consulta del número de productos agrupados por categoría.

### Modified Capabilities
- Ninguna.

## Impact

- API HTTP de lectura del catálogo y sus puertos/casos de uso.
- Adaptador MongoDB y pruebas de integración del catálogo.

## Non-goals

- Filtrar el recuento por estado, texto u otros criterios de búsqueda.
- Crear, editar o listar categorías independientemente de los productos.
- Cambiar el contenido o el contrato de `GET /api/products`.
