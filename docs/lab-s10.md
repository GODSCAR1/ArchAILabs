# Laboratorio S10 — retiro de PostgreSQL del catálogo

## Ficha antes/después

| Operación de ficha | Antes: PostgreSQL | Después: MongoDB |
| --- | --- | --- |
| Consultas de lectura | 4 consultas SQL para producto, categoría, variantes y atributos/reseñas | 1 operación de lectura del documento agregado |

La operación Mongo se valida con `ProductDetailQueryCountMongoTest`, bajo el escenario
`@DisplayName("Muchas colecciones asociadas")`. El documento agregado incluye los datos de detalle
necesarios; las reseñas paginadas se consultan con su adaptador Mongo.

La proyección periódica de stock es independiente: `CatalogStockProjectionJob` continúa leyendo
`stock` mediante JDBC parametrizado por lotes. No forma parte del coste de lectura del detalle.

Flyway V7 retira las tablas PostgreSQL del catálogo y conserva las tablas de inventory y ordering.
Antes de desplegar esta migración, MongoDB debe contener el catálogo autoritativo respaldado.
