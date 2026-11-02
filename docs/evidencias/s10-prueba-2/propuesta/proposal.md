# Proposal

## Why

El buscador del catálogo no permite limitar resultados por marca, aunque la información de atributos del producto puede almacenar ese dato. Añadir el filtro permitirá combinar la marca con los filtros existentes sin ampliar los datos de respuesta ni cambiar el flujo de búsqueda.

## What Changes

- Añadir el parámetro opcional `brand` a `GET /api/products`, aplicado como igualdad sin distinguir mayúsculas sobre `attributes.brand`.
- Combinar el filtro por marca con todos los filtros existentes y mantener la paginación y el orden actuales.
- Declarar un índice para el campo filtrado y comprobar su creación en la prueba de índices del catálogo.

## Capabilities

### New Capabilities

- Ninguna.

### Modified Capabilities

- `product-search`: ampliar los filtros combinables con una igualdad de marca sobre el atributo `brand`.

## Impact

- Afecta al contrato de `GET /api/products`, al mapeo de criterios de búsqueda y a la consulta MongoDB del catálogo.
- Afecta a la inicialización y las pruebas de índices del catálogo.
- No añade dependencias ni requiere cambios de esquema del documento: la marca se lee del mapa existente de atributos.

## Non-goals

- Añadir un campo de marca independiente ni cambiar las operaciones de creación o edición.
- Añadir un endpoint para enumerar marcas, autocompletado o nuevos filtros de atributos.
- Cambiar las respuestas resumidas, las reglas de paginación o el comportamiento de los filtros existentes.
