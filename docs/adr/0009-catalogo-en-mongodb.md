# ADR 0009: catálogo en MongoDB

- **Estado:** propuesto
- **Fecha:** 2026-11-01
- **Contexto:** transición del catálogo completo a MongoDB con PostgreSQL disponible durante coexistencia; véase [docs/catalog-model.md](../catalog-model.md)

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

Esta alternativa usa `CatalogProduct` como documento del catálogo. Embebe las pocas variantes,
los atributos variables, la referencia extendida de categoría, `availableStock`, `rating` y
hasta cinco `latestReviews`. Las reseñas completas permanecen en una colección aparte. El
modelo evita joins para la ficha, limita los arrays y conserva la paginación de reseñas sin
hacer crecer el documento principal.

El stock copiado no cambia el dueño del dato: `stock` sigue siendo la fuente de verdad y
la copia solo soporta el indicador de catálogo `inStock`. Los índices se centralizan en
`CatalogIndexInitializer` y las consultas se expresan con `CatalogProduct.class`.

## Decisión

Se elige **B: MongoDB como almacén del catálogo en modo mongo**, con el modelo descrito en
[catalog-model.md](../catalog-model.md). A2 sería una alternativa competitiva en producción:
ofrece una ficha en una consulta, un solo motor, transacciones y menor coste operativo. Se
elige B porque el objetivo de este lab es practicar el modelado documental y la migración
gradual, y porque MongoDB aporta validación `$jsonSchema`, índices declarados sobre el
documento, agregaciones y el soporte documental de Spring Data descritos en A2.

`catalog.store` selecciona conjuntamente los adaptadores de lectura y escritura de búsqueda,
ficha, creación y edición. En `mongo`, MongoDB es la fuente de verdad del catálogo. El valor
predeterminado final es `mongo`; el modo explícito `postgres` permanece para coexistencia y
rollback tras reconciliación. La infraestructura Mongo se activa cuando `catalog.store=mongo`
o `catalog.migration.enabled=true`.

La transición sigue el plan expandir-contraer: se mantiene `catalog.store=postgres` y
`catalog.migration.enabled=true` hasta que el checkpoint de migración indique `COMPLETADO`.
Entonces se cambia a `catalog.store=mongo` y `catalog.migration.enabled=false`. No se añade un
barrido final automático ni se borran las tablas PostgreSQL en este change. Su retirada requiere
otro change independiente después de confirmar consumidores y rollback.

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

Durante la coexistencia, la migración PostgreSQL→Mongo actualiza también `category.name`,
`availableStock`, `rating` y `latestReviews`. Tras el corte se desactiva esa migración para que
no sobrescriba escrituras Mongo. Un job independiente copia periódicamente `stock.available`:
recorre productos Mongo por keyset de `_id`, lee IDs por lotes con `product_id in (:ids)` y
actualiza en bulk; si falta una fila, asigna cero. El desfase hasta el siguiente ciclo se acepta
para el indicador del catálogo; la reserva continúa validándose contra `stock`.

SKU duplicado mantiene HTTP 409 con `ProblemDetail.type=about:blank` por compatibilidad. Es una
deuda de contrato explícita, no una decisión sobre el tipo final del error.

El rollback después del corte requiere pausar escrituras, reconciliar Mongo→PostgreSQL
(productos, versiones y SKUs) y verificar la reconciliación antes de cambiar a
`catalog.store=postgres`. Sin ella, las escrituras existentes solo en Mongo no serán visibles
desde PostgreSQL.

## Consecuencias

- La ficha puede leerse como un documento agregado, con arrays acotados y sin joins entre
  variantes, atributos y categoría.
- Las reseñas completas no tienen límite de crecimiento dentro de `CatalogProduct`; solo se
  mantiene una ventana de cinco reseñas recientes y un resumen de rating.
- Durante la transición existe desfase entre PostgreSQL y MongoDB. PostgreSQL se selecciona
  explícitamente hasta el checkpoint `COMPLETADO`; luego el valor por defecto `mongo` usa el
  catálogo migrado y sus escrituras.
- El rollback después del corte no es inmediato: requiere reconciliar productos, versiones y
  SKUs Mongo→PostgreSQL antes de cambiar el selector.
- El indicador `availableStock` puede quedar desfasado hasta el siguiente lote del job; el
  inventario y las reservas siguen teniendo PostgreSQL como fuente de verdad.
- El tipo `about:blank` para un SKU duplicado conserva el contrato actual y queda como deuda.
- `src/test/resources/config/application.properties` fija `catalog.store=postgres` para los
  `@SpringBootTest` sin almacén declarado (inventory, ordering, IT JPA y guardianes JPA), que no
  requieren Mongo. Las pruebas que necesitan Mongo lo seleccionan explícitamente. Esta propiedad
  solo afecta al classpath de tests: `CatalogStoreConfigurationTest` comprueba el default de
  producción con `ApplicationContextRunner`, sin cargar ese archivo. Se retirará junto con
  `retire-catalog-postgres`.
- Se mantiene el coste operativo de dos almacenes hasta completar expandir-contraer, incluida
  la monitorización de antigüedad de la re-migración y la reconciliación.
- MongoDB no se convierte en dueño de `stock` ni de las reglas de reserva; solo materializa
  una vista derivada para catálogo.
- La equivalencia del orden por `name` con `Collation.simple()` de MongoDB requiere que PostgreSQL
  use la collation `C` (orden por punto de código), o que MongoDB use una collation de idioma
  equivalente a la collation PostgreSQL configurada.
- La eliminación de tablas PostgreSQL queda fuera de este ADR y requiere otro change revisado,
  con pruebas de rollback y comprobación de consumidores.
