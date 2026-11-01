# ADR 0009: catálogo en MongoDB

- **Estado:** propuesto
- **Fecha:** 2026-11-01
- **Contexto:** migración del modelo de lectura del catálogo; véase [docs/catalog-model.md](../catalog-model.md)

## Contexto

El catálogo combina una ficha que siempre muestra variantes, atributos, categoría, stock,
valoración y reseñas recientes con un buscador que necesita solo un resumen. En PostgreSQL,
la ficha cuesta hoy cuatro consultas, como verifica `ProductDetailQueryCountTest`, y el
buscador cuesta dos, como verifica `ProductSearchQueryCountTest`. Las reseñas además crecen
sin límite y necesitan paginación.

Los atributos son variables por categoría y el documento de lectura tiene una forma natural
para contener variantes, atributos, una referencia extendida de categoría y un resumen de
reseñas. La motivación de este ADR es evaluar un almacén de lectura documental sin convertir
la migración en un cambio irreversible ni hacer que la copia de stock sea una nueva fuente de
verdad. El modelo concreto está documentado en [catalog-model.md](../catalog-model.md).

## Alternativas

### A. Seguir en PostgreSQL con `jsonb` para atributos e índices GIN

Esta alternativa conservaría PostgreSQL como almacén del catálogo y movería los atributos
a una columna `jsonb`, con índices GIN para búsquedas por claves y valores. Resolvería
la variabilidad del esquema sin añadir otro motor, mantendría las transacciones y facilitaría
la consistencia entre producto, categoría, reseñas e inventario. También podría mejorar filtros
sobre atributos frente a muchas columnas específicas.

No resolvería, por sí sola, el patrón de lectura de la ficha. Seguirían siendo necesarias las
cuatro consultas actuales, o una consulta con joins y agregados que concentraría más trabajo,
duplicaría filas y complicaría la paginación de reseñas y la carga acotada de `latestReviews`.
`jsonb` tampoco cambia el crecimiento ilimitado de las reseñas ni elimina la necesidad de
mantener un resumen de rating. Los índices GIN tienen coste de escritura, consumo de espacio y
no sustituyen índices relacionales para SKU, ordenación o concurrencia de stock.

Se descarta como decisión principal porque mejora la flexibilidad de atributos, pero no ataca
la razón principal de este modelo: una lectura documental denormalizada para la ficha y el
buscador. Sigue siendo una alternativa válida si la operación no justifica mantener un segundo
almacén.

### A2. Tabla de lectura PostgreSQL con el documento desnormalizado en `jsonb`

Esta alternativa mantendría PostgreSQL como almacén de escritura y añadiría una tabla de
lectura con el documento completo de `CatalogProduct` en una columna `jsonb`. La ficha podría
resolverse en una consulta, igual que en MongoDB, y usaría la misma re-migración periódica
desde las tablas actuales. El buscador podría consultar la tabla de lectura y sus índices
relacionales o sobre `jsonb`.

Gana simplicidad operativa: hay un solo motor, un único conjunto de backups, una sola
monitorización y transacciones PostgreSQL disponibles si se necesita coordinar lecturas y
escrituras. También reduce el coste de infraestructura y hace más sencillo el rollback
operativo que mantener PostgreSQL y MongoDB.

Pierde capacidades específicas frente a MongoDB. La validación `$jsonSchema` y los índices
declarados sobre el documento no están disponibles con la misma semántica; habría que
reproducirlos con restricciones, triggers, código de aplicación o índices de expresión. Las
agregaciones sobre el documento serían menos naturales y Spring Data no ofrecería el mismo
modelo documental de `MongoTemplate`/repositorios sobre `CatalogProduct`; habría que mantener
SQL, proyecciones y conversiones JSON. Además, una tabla `jsonb` sigue compartiendo recursos y
contención con las cargas transaccionales de PostgreSQL.

A2 es competitiva en producción: resuelve la ficha en una consulta y conserva las ventajas
operativas, transaccionales y de backup de un solo motor. No se descarta por rendimiento, sino
porque el objetivo de este lab es practicar el modelado documental y una migración gradual a
MongoDB.

### B. MongoDB con el modelo de `catalog-model.md`

Esta alternativa usa `CatalogProduct` como documento de lectura. Embebe las pocas variantes,
los atributos variables, la referencia extendida de categoría, `availableStock`, `rating` y
hasta cinco `latestReviews`. Las reseñas completas permanecen en una colección aparte. El
modelo evita joins para la ficha, limita los arrays y conserva la paginación de reseñas sin
hacer crecer el documento principal.

El stock copiado no cambia el dueño del dato: `stock` sigue siendo la fuente de verdad y
la copia solo soporta el indicador de catálogo `inStock`. Los índices se centralizan en
`CatalogIndexInitializer` y las consultas se expresan con `CatalogProduct.class`.

## Decisión

Se elige **B: MongoDB como almacén de lectura del catálogo**, con el modelo descrito en
[catalog-model.md](../catalog-model.md). A2 sería una alternativa competitiva en producción:
ofrece una ficha en una consulta, un solo motor, transacciones y menor coste operativo. Se
elige B porque el objetivo de este lab es practicar el modelado documental y la migración
gradual, y porque MongoDB aporta validación `$jsonSchema`, índices declarados sobre el
documento, agregaciones y el soporte documental de Spring Data descritos en A2.

PostgreSQL permanece durante la transición y sigue siendo el almacén actual hasta que la
nueva lectura esté validada.

La selección se expone detrás del feature flag `catalog.read-store`, cuyos valores son:

- `postgres` por defecto durante la transición.
- `mongo` para activar la lectura desde MongoDB tras validar paridad y métricas.

La transición sigue el plan expandir-contraer: primero se añade y rellena el nuevo almacén,
se valida la lectura y se cambia el flag; las tablas PostgreSQL no se borran en este cambio.
Solo se eliminarán al final, en otro change independiente, después de confirmar que no quedan
consumidores ni posibilidad de rollback inmediato.

Esta decisión cambiaría si el coste operativo, la monitorización o la reconciliación de un
segundo motor no se justifican en producción, si el desfase de la re-migración resulta
inaceptable o si las necesidades reales no requieren las capacidades documentales de MongoDB.
En ese caso se revisaría A2 antes de eliminar PostgreSQL.

### Estrategia de escrituras durante la transición

Se consideran tres estrategias:

- **A. Doble escritura:** escribir PostgreSQL y MongoDB en la misma operación. Se descarta como
  mecanismo principal porque una caída o timeout de la segunda escritura puede dejar ambos
  almacenes divergentes; una transacción local no garantiza atomicidad entre PostgreSQL y
  MongoDB y los reintentos pueden duplicar o reordenar actualizaciones.
- **B. Re-migración periódica:** PostgreSQL conserva la escritura de negocio y un proceso
  periódico reconstruye o actualiza el modelo de lectura MongoDB desde la fuente actual. Se
  elige porque es operativamente simple, permite repetir la carga, hace visible el desfase y
  evita que una petición de negocio dependa de dos confirmaciones de escritura.
- **C. Eventos con outbox:** publicar un evento transaccional en una outbox de PostgreSQL y
  procesarlo de forma idempotente para actualizar MongoDB. Sería la opción adecuada para menor
  desfase, pero requiere outbox, consumidores durables, reintentos, deduplicación, ordenación,
  observabilidad, replay y reconciliación de eventos perdidos o mal formados. Es un cambio de
  infraestructura mayor y no se introduce ahora.

La re-migración actualiza también `category.name`, `availableStock`, `rating` y
`latestReviews`. El desfase de `availableStock` se acepta únicamente para la lectura
indicativa del catálogo; la reserva continúa validándose contra `stock`.

## Consecuencias

- La ficha puede leerse como un documento agregado, con arrays acotados y sin joins entre
  variantes, atributos y categoría.
- Las reseñas completas no tienen límite de crecimiento dentro de `CatalogProduct`; solo se
  mantiene una ventana de cinco reseñas recientes y un resumen de rating.
- Durante la transición existe desfase entre PostgreSQL y MongoDB. El flag por defecto en
  `postgres` y la re-migración repetible reducen el riesgo de activar datos incompletos.
- Se mantiene el coste operativo de dos almacenes hasta completar expandir-contraer, incluida
  la monitorización de antigüedad de la re-migración y la reconciliación.
- MongoDB no se convierte en dueño de `stock` ni de las reglas de reserva; solo materializa
  una vista derivada para catálogo.
- La equivalencia del orden por `name` con `Collation.simple()` de MongoDB requiere que PostgreSQL
  use la collation `C` (orden por punto de código), o que MongoDB use una collation de idioma
  equivalente a la collation PostgreSQL configurada.
- La eliminación de tablas PostgreSQL queda fuera de este ADR y requiere otro change revisado,
  con pruebas de rollback y comprobación de consumidores.
