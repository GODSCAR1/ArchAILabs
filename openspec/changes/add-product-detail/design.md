# Design

## Context

El catálogo ya expone búsqueda paginada y un read model inmutable de stock en persistencia, mientras la API de edición recibe un UUID. La propuesta añade tres colecciones de detalle en PostgreSQL y dos lecturas web sin cambiar el buscador ni depender de la API transaccional de inventory. La motivación y el alcance están en `proposal.md`; los contratos están en `specs/product-detail/spec.md`.

## Goals / Non-Goals

**Goals:**

- Mantener la arquitectura hexagonal: dominio y aplicación sin dependencias de Spring/JPA/Jackson, y conversiones en mappers de infraestructura.
- Resolver la ficha en exactamente cuatro consultas: producto con categoría y stock, variantes, atributos y reseñas con agregados y las cinco primeras.
- Mantener las reseñas completas como una lectura paginada independiente, con conteo total y orden determinista.
- Hacer que Flyway V6 sea aditiva, sin semillas ni operaciones de escritura desde los endpoints de lectura.

**Non-Goals:**

- Cambiar el modelo o la consulta del buscador existente.
- Compartir entidades JPA entre catalog e inventory.
- Añadir una API de gestión de variantes, atributos o reseñas.
- Diseñar todavía la futura persistencia MongoDB.

## Decisions

### Modelo y migración

Crear V6 con tres conjuntos de filas ligados al producto. Las variantes tendrán UUID, sufijo de SKU, color, talla y precio adicional entero en COP; los atributos usarán `(product_id, name)` como identidad; las reseñas tendrán UUID, autor, valoración con restricción `1..5`, comentario y fecha de creación. V6 creará en `reviews` el índice `(product_id, created_at DESC, id)` para las cinco últimas reseñas y la paginación; variantes tendrá un índice por `product_id`, y en atributos la clave primaria `(product_id, name)` cubrirá ese acceso. La migración no insertará datos. Las relaciones serán de lectura y no se modelarán como agregados con vida independiente.

### Lectura de la ficha

Añadir un puerto de salida de consulta de detalle con resultados de proyección, un servicio de aplicación y un puerto de entrada. El adaptador de persistencia usará cuatro consultas separadas y parametrizadas para evitar el producto cartesiano de unir tres colecciones. La primera localizará por SKU y traerá categoría y stock; la segunda y tercera cargarán variantes y atributos por `product_id`; la cuarta calculará `average` como `round(avg(rating)::numeric, 2)` en SQL, con redondeo HALF_UP y valor `0` sin reseñas, calculará `count` y recuperará las cinco reseñas más recientes. La futura migración a MongoDB calculará el mismo valor y el test de equivalencia lo comparará con `compareTo`. La ausencia de stock se mapeará a cero. La respuesta se construirá mediante mappers web dedicados.

La suma de cuatro consultas se verificará con datos que superen el batch fetch configurado, `flush()` y `clear()`, y estadísticas Hibernate reiniciadas. La página completa de reseñas usará su consulta de contenido y su consulta de total; ese coste queda fuera del límite de la ficha.

### Reseñas y paginación

La consulta de reseñas validará que el producto existe por SKU antes de devolver contenido. Usará `page=0` y `size=20` por defecto, normalizará tamaños mayores a `100`, y ordenará por `created_at DESC, id ASC`. La salida será una proyección/DTO de lectura y nunca una entidad persistente.

### Rutas y errores

La edición conserva `PUT /api/products/{id}` y su conversión UUID. La nueva lectura usa `GET` sobre el mismo patrón textual, por lo que no hay colisión de routing: el verbo separa ambos contratos y Spring convierte el path a UUID únicamente en PUT. `GET /api/products/{sku}/reviews` es una ruta más específica y se declara junto a la ficha. SKU inexistente reutilizará el `ProblemDetail` de producto no encontrado y su type estable; no se crearán types nuevos sin necesidad contractual.

### Reglas de arquitectura

- `catalog.domain` SHALL depender solo de Java y no de Spring, JPA ni Jackson.
- `catalog.application` SHALL depender solo de `catalog.domain` y sus puertos, nunca de infrastructure.
- Los puertos de salida de detalle SHALL vivir en `catalog.domain.port`; sus adaptadores de consulta SHALL vivir en `catalog.infrastructure.adapters.out.persistence`.
- Los DTOs HTTP, entidades de persistencia y mappers SHALL permanecer en infrastructure; ningún modelo de dominio SHALL conocerlos.
- `catalog` SHALL NOT depender de clases de `inventory`; el stock será una proyección de lectura ya existente en la frontera de persistencia.
- `inventory` SHALL NOT depender de clases de `catalog`.
- La composición de servicios, puertos y adaptadores SHALL ocurrir en `catalog.infrastructure.config` mediante `@Bean`.

### Alternativas consideradas

- Una única consulta con varias uniones se descarta porque multiplicaría filas entre variantes, atributos y reseñas, complicaría los agregados y podría reintroducir N+1 al materializar colecciones.
- Cargar asociaciones lazy desde el servicio se descarta porque el coste crecería con cada fila y puede producir `LazyInitializationException` fuera de la transacción.
- Consultar stock mediante la API pública de inventory se descarta porque añade latencia por producto y rompe el objetivo de coste fijo; el buscador ya establece el patrón del read model local.

## Risks / Trade-offs

- [Risk] La ficha fija cuatro consultas pero cada consulta puede devolver muchas filas → Mitigation: el detalle declara pocas variantes por producto, usa proyecciones y mantiene paginación solo para reseñas completas.
- [Risk] El promedio puede perder precisión si se serializa como entero → Mitigation: calcularlo en SQL con dos cifras y redondeo HALF_UP, definirlo como número decimal en el DTO y comprobar `4.33` para `5, 4, 4` en pruebas de integración.
- [Risk] La tabla de reseñas crecerá sin límite → Mitigation: índice `(product_id, created_at DESC, id)` para las lecturas; la estrategia de archivado queda fuera de este cambio.
- [Risk] Compartir el read model de stock acopla el esquema de catalog e inventory → Mitigation: mantenerlo inmutable, solo de lectura, y proteger la dirección de dependencias con ArchUnit.
- [Risk] Un SKU con forma de UUID puede parecer una ruta de edición → Mitigation: la edición solo acepta PUT y la ficha solo GET; probar ambos contratos explícitamente.

## Migration Plan

1. Aplicar Flyway V6, que crea las tres estructuras, restricciones e índices sin datos.
2. Desplegar los modelos de lectura y adaptadores después de que V6 esté aplicada.
3. Ejecutar pruebas de migración, contratos HTTP, orden de reseñas y límite de cuatro consultas con PostgreSQL/Testcontainers.
4. Para rollback de aplicación, retirar los endpoints y volver a la versión anterior; no ejecutar una migración destructiva automática. Si se requiere revertir el esquema, crear una migración explícita revisada.
