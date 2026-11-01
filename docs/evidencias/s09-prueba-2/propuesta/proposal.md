# Proposal

## Why

El listado de productos ya permite ordenar por nombre, precio y fecha de creación, pero no permite priorizar productos según su disponibilidad. Añadir la ordenación por stock permite a los clientes localizar primero los productos con más o menos unidades sin cambiar el contrato de respuesta.

## What Changes

- Aceptar `stock` como campo válido del parámetro `sort` del listado de productos.
- Ordenar ascendente o descendentemente por la cantidad de stock asociada a cada producto; la ausencia de registro equivale a `0`.
- Mantener `id` como criterio final ascendente para que la paginación sea estable cuando varias cantidades de stock coincidan.
- Rechazar como hasta ahora los campos de ordenación no incluidos en la lista blanca.

## Capabilities

### Modified Capabilities

- `product-search`: ampliar la ordenación permitida y estable del listado para incluir `stock`.

## Non-goals

- No cambiar el formato ni el contenido de la respuesta del listado.
- No añadir filtros nuevos de stock.
- No cambiar los campos de ordenación existentes ni sus valores por defecto.

## Impact

- Afecta al parámetro HTTP `sort` de `GET /api/products`.
- Afecta al mapper web, al adaptador de persistencia y a las pruebas de búsqueda/paginación del catálogo.
- No requiere nuevas dependencias ni cambios de esquema de base de datos.
