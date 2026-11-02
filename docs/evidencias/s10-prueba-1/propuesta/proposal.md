# Proposal

## Why

La ficha de producto realiza una operación adicional para traer reseñas, aunque las reseñas visibles se consultan junto con el producto. Incluirlas en el documento del producto permite resolver la ficha en una sola lectura, a cambio de que el tamaño del documento crezca con cada reseña; esta propuesta revisa explícitamente la regla arquitectónica vigente que separa las reseñas completas.

## What Changes

- **BREAKING**: la ficha del producto contendrá todas sus reseñas completas y la respuesta de detalle podrá incluirlas desde la lectura del documento agregado.
- La paginación de reseñas se servirá desde las reseñas incluidas en el producto, manteniendo su orden y metadatos.
- Se retirará la colección independiente de reseñas y se definirán migración de datos y comportamiento para productos con documentos demasiado grandes.
- Se medirán las lecturas de ficha y se comprobará el coste y límite de tamaño ante productos con muchas reseñas.

## Capabilities

### New Capabilities

- `product-detail`: consulta de ficha y reseñas completas incluidas en el documento de producto.

### Modified Capabilities

<!-- No hay una capability product-detail en openspec/specs/; se crea el contrato de ficha. -->

## Impact

- Afecta al documento persistido de producto, alta y consulta de reseñas, consulta de detalle, consulta paginada y pruebas de número de operaciones.
- Requiere migrar los documentos y reseñas existentes, actualizar el validador e índices del catálogo y decidir una estrategia para mantener el límite BSON de 16 MB.
- Cambia el contrato de lectura del catálogo y contradice la decisión registrada en `docs/adr/0009-catalogo-en-mongodb.md` y `docs/catalog-model.md`; esos documentos requieren revisión explícita.

## Non-goals

- Cambiar la propiedad o la validación transaccional del stock.
- Cambiar campos, orden o paginación expuestos por la API, salvo el origen de datos de las reseñas.
- Cambiar la valoración agregada (`rating`) o los flujos de búsqueda de productos.
