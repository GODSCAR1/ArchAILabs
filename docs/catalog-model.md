# Modelo de catálogo

Este documento describe el modelo de lectura previsto para el catálogo en MongoDB y lo
compara con el coste actual en PostgreSQL. Las frecuencias no proceden de una instalación
con tráfico real: todas están marcadas como **SUPUESTO** y se basan en la proporción típica
de lecturas frente a escrituras de un catálogo, o en los tests existentes.

## Patrones de acceso

| Operación | Lectura/escritura | Frecuencia estimada | Campos que lee o escribe | Filtros/orden | Consultas hoy en PostgreSQL | Consultas esperadas en MongoDB |
|---|---|---|---|---|---|---|
| `GET /api/products/{sku}` (ficha) | Lectura | **SUPUESTO: 1, base de comparación.** Proporción típica de catálogo; no hay tráfico real | Lee producto, categoría, `stock`/`availableStock`, variantes, atributos, rating y hasta cinco reseñas recientes | Búsqueda por `sku`; reseñas recientes por `created_at DESC, id ASC` | **4**, medido por `ProductDetailQueryCountTest`: producto/categoría/stock, variantes, atributos y reseñas agregadas | **1**: documento agregado |
| `GET /api/products` (buscador) | Lectura | **SUPUESTO: 5-20 por cada ficha.** Navegación suele generar más búsquedas que fichas; contraste con `ProductSearchQueryCountTest` | Lee campos de resumen de producto, nombre de categoría y stock; no carga el detalle completo | Filtros combinables; orden permitido con `id` como desempate; paginación | **2**, medido por `ProductSearchQueryCountTest`: página y `count` | **2**: página y `count` |
| Reseñas paginadas de un producto | Lectura | **SUPUESTO: 0,1-0,5 por cada ficha.** La mayoría de visitas no pagina reseñas; basado en el flujo de `ProductReviewQueryIT` | Lee reseñas `{id, author, rating, comment, createdAt}` y metadatos de página | `product_id`; `created_at DESC, id ASC`; página y tamaño normalizado | **2 por página**, consulta de contenido y `count`; no existe medición de tráfico | **3**: comprobación de ficha, contenido y `count` |
| Crear producto | Escritura | **SUPUESTO: 0,001-0,01 por cada ficha.** Administración ocasional frente a navegación; basado en `CreateProductServiceTest` | `CreateProductService` valida SKU y escribe solo `products` mediante `ProductRepository`; genera los campos del producto | Validación de SKU único | **No medido como contador:** una comprobación de SKU y un `save` en el flujo actual | **1**: inserción del documento |
| Editar producto (`@Version`) | Escritura | **SUPUESTO: 0,001-0,01 por cada ficha.** Edición administrativa; basado en `EditProductServiceTest` y `ProductEditingIT` | Lee y escribe solo `name` y `price`; comprueba la `version` esperada | Identidad del producto y versión; conflicto concurrente responde `409` | **No fijado por un test de conteo:** lectura del producto y guardado con `@Version`, con el `SELECT` adicional del `merge` documentado en el ADR 0007 | **1**: reemplazo/actualización del documento |
| Reserva de stock (`inventory`) | Escritura | **SUPUESTO: 0,05-0,2 por cada ficha.** Concentrada en compra; basada en los tests de concurrencia, no en tráfico real | Cambia `stock.available`; el catálogo copia `availableStock` como dato derivado | Identidad del producto/stock; actualización condicional o bloqueo según la ruta | **Coste dependiente de la ruta:** `findExistingProductIds` y actualizaciones `discountIfAvailable`; no hay un número fijo por petición | No aplica: MongoDB no es dueño de `stock` |
| Añadir un atributo nuevo | Escritura de configuración | **SUPUESTO: 0,001-0,01 por cada ficha.** Operación administrativa ocasional; basada en el modelo V6, no en tráfico real | Inserta una fila `(product_id, name, value)` en `product_attributes`; es un mapa genérico clave-valor, no una definición por categoría | Identidad de producto y clave `name`; clave única por producto | **No medido:** inserción en `product_attributes`, con sus comprobaciones de integridad | **1**: actualización del documento de catálogo |

Los números de las columnas de consultas son el estado **HOY** en PostgreSQL y la expectativa
del modelo MongoDB, respectivamente. Donde no existe un test de conteo específico, se indica
expresamente que no está medido en lugar de inventar una cifra.

## Relaciones y decisiones

| Relación | Modelo en `CatalogProduct` | Decisión y motivo |
|---|---|---|
| Producto -> variantes | `variants` embebidas | Son pocas, no tienen vida propia y se leen siempre junto con la ficha. Cada variante es `{id, skuSuffix, color, size, additionalPrice}` y el array tiene un máximo de 100 elementos. |
| Producto -> atributos | `attributes` embebidos como mapa | La forma es variable por categoría. Embebido permite leer los atributos de la ficha sin joins ni una colección de atributos independiente. |
| Producto -> categoría | Extended reference `{ "id": "...", "name": "..." }` | La categoría tiene vida propia, pero el nombre aparece tanto en la ficha como en el buscador. En la base de datos, el campo persistido es `category._id`; el nombre copiado se actualiza durante la re-migración. |
| Producto -> reseñas | Colección `reviews` aparte, más `latestReviews` y `rating` en el producto | Las reseñas crecen sin límite y no deben hacer crecer el documento. `latestReviews` está limitado a 5. `rating.average` usa 2 decimales y redondeo `HALF_UP`; `rating.count` evita recalcular el total en la ficha. |
| Producto -> stock | `availableStock` copiado en el producto | Es un dato derivado de `stock.available`; el dueño sigue siendo `stock`. La re-migración actualiza la copia. Se acepta desfase para el indicador `inStock`, que no sustituye a la reserva transaccional. |

## Documento de ejemplo

`_id` es el UUID del producto. El precio es un entero en COP, sin decimales.

```json
{
  "_id": "7f4d5c8e-6b3a-4f4f-9a6e-1c7d2b8e5a10",
  "sku": "CAMISA-LINO-AZUL-M",
  "name": "Camisa de lino azul",
  "price": 189900,
  "status": "ACTIVE",
  "createdAt": "2026-11-01T15:30:00Z",
  "version": 3,
  "category": {
    "_id": "2a3b4c5d-6e7f-4890-ab12-cd34ef567890",
    "name": "Camisas"
  },
  "variants": [
    {
      "id": "4b6c8d0e-2f1a-4b3c-9d5e-7f8a0b1c2d34",
      "skuSuffix": "-M",
      "color": "Azul",
      "size": "M",
      "additionalPrice": 0
    }
  ],
  "attributes": {
    "material": "Lino",
    "fit": "Regular"
  },
  "availableStock": 18,
  "rating": {
    "average": 4.67,
    "count": 12
  },
  "latestReviews": [
    {
      "id": "91ab2c3d-4e5f-4678-9012-ab345c678901",
      "author": "Cliente 42",
      "rating": 5,
      "comment": "La tela es fresca y la talla corresponde.",
      "createdAt": "2026-10-30T10:15:00Z"
    }
  ]
}
```

Una reseña completa vive en la colección `reviews`:

```json
{
  "_id": "91ab2c3d-4e5f-4678-9012-ab345c678901",
  "productId": "7f4d5c8e-6b3a-4f4f-9a6e-1c7d2b8e5a10",
  "author": "Cliente 42",
  "rating": 5,
  "comment": "La tela es fresca y la talla corresponde.",
  "createdAt": "2026-10-30T10:15:00Z"
}
```

## Reglas para agentes

- Los arrays deben estar acotados: `latestReviews` tiene como máximo 5 elementos y `variants` tiene como máximo 100.
- Todo índice se declara en `CatalogIndexInitializer`; no se crean índices dispersos en adaptadores, migraciones ad hoc o consultas de arranque.
- Las consultas y agregaciones se construyen con la clase de entrada `CatalogProduct.class`, nunca con el nombre literal de la colección.
- `availableStock` es una copia de lectura: las reservas escriben en `stock`, no en el documento de catálogo como fuente de verdad.

## Índices

Los índices los crea `CatalogIndexInitializer`:

- `sku` único.
- `{ "category._id": 1, "price": 1 }` para el buscador.
- Atributos filtrables: ninguno todavía. El buscador actual no filtra por atributos; cada nuevo filtro sobre atributos debe añadir aquí su índice correspondiente.

La decisión arquitectónica asociada está en [ADR 0009: catálogo en MongoDB](adr/0009-catalogo-en-mongodb.md).
