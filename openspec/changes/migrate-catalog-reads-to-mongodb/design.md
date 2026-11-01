# Design

## Context

El catálogo ya expone los puertos `ProductDetailRepository` y `ProductSearchRepository`; los
servicios de aplicación y los controladores consumen esos puertos y no conocen JPA. La
implementación actual reconstruye la ficha y las reseñas desde PostgreSQL, mientras que
`ProductConfiguration` ensambla los adaptadores y abre las transacciones mediante
`TransactionTemplate`. El modelo y las restricciones documentales son los definidos en
`docs/catalog-model.md` y ADR 0009.

El `pom.xml` ya contiene los starters de Spring Data MongoDB y Testcontainers para MongoDB, por
lo que el diseño reutiliza esas dependencias y no introduce otra librería de mapeo.

## Goals / Non-Goals

**Goals:**

- Añadir documentos de lectura y sus mappers dedicados en infraestructura de `catalog`.
- Materializar productos y reseñas desde las proyecciones/datos JPA actuales con batches keyset
  por UUID, upsert por SKU y checkpoint persistente.
- Mantener una sola consulta MongoDB para la ficha agregada y dos consultas para una página de
  reseñas, separando el documento principal de la colección de reseñas completas.
- Seleccionar adaptadores JPA o Mongo con `catalog.read-store`, dejando `postgres` como fallback,
  y permitir que la migración alimente Mongo mientras las lecturas permanecen en PostgreSQL.
- Centralizar la creación explícita de índices y del validador `$jsonSchema`, con auto-indexación
  desactivada.
- Hacer que la misma suite de API se ejecute contra ambos modos con datos preparados primero en
  PostgreSQL y migrados en el modo Mongo.

**Non-Goals:**

- No mover `ProductRepository`, `ProductEditingRepository` ni las escrituras de stock a MongoDB.
- No cambiar los puertos de dominio ni crear un puerto `CatalogReader` adicional.
- No eliminar adaptadores JPA, tablas PostgreSQL, outbox, eventos ni filtros nuevos.

## Decisions

### Modelo y fronteras

Se crearán los documentos `CatalogProduct`, `CategoryRef`, `Variant`, `RatingSummary` y
`ReviewSnippet` en un subpaquete Mongo de `catalog.infrastructure.adapters.out`, y un documento de
reseña completo en una colección separada. `CatalogProduct` usará el UUID del producto como `_id`,
SKU único, categoría con `_id` y nombre, atributos como mapa y `availableStock` como copia derivada.
Los mappers serán clases `final`, sin estado y con métodos estáticos, siguiendo la regla de una
conversión por frontera; ni dominio ni aplicación importarán tipos de Spring Data Mongo.

### Consultas y selección

Los adaptadores Mongo implementarán directamente los dos puertos existentes. El adaptador de
detalle leerá un `CatalogProduct` por SKU en una operación y construirá `ProductDetail` desde el
documento; el método de reseñas consultará la colección separada para contenido y recuento. El
adaptador de búsqueda traducirá `ProductSearchCriteria` a filtros tipados, whitelist de ordenación,
desempate por UUID y paginación, preservando el comportamiento de la especificación existente.

La configuración declarará beans alternativos con `@ConditionalOnProperty`:
`catalog.read-store=mongo` activa Mongo y la ausencia de la propiedad o `postgres` activa JPA.
La configuración compartirá los casos de uso de aplicación, pero no mezclará adaptadores de
lectura. Los beans de escritura no quedan condicionados. `catalog.read-store` selecciona el
adaptador de lectura; `catalog.migration.enabled` controla separadamente el job. El inicializador
de índices, el validador y el job se activan si `catalog.read-store=mongo` O
`catalog.migration.enabled=true`, permitiendo copiar datos a Mongo mientras las lecturas aún usan
PostgreSQL.

### Migración, checkpoint y consistencia

Un servicio de migración leerá lotes ordenados por `id > lastProcessedId`, con límite configurable,
y persistirá `{lastProcessedId, estado}` tras confirmar cada lote. `EN_CURSO` reanuda una ejecución
interrumpida desde el último UUID confirmado; al llegar al final el estado pasa a `COMPLETADO`. El
siguiente ciclo reinicia el cursor desde el principio y marca el barrido `EN_CURSO`, para propagar
cambios de stock, precio o categoría sin detección de productos modificados. El checkpoint se
almacena en MongoDB en una colección técnica con clave estable; upsert por SKU y reemplazo/upsert de
reseñas por id hacen repetible el último lote. El informe incluye productos, variantes, reseñas y
rechazos con SKU.

Los productos con más de 100 variantes no se materializan: se cuentan como rechazados con su SKU y
la migración continúa con el resto del lote. El mapper recorta `latestReviews` a cinco. El validador
`$jsonSchema` actúa como segunda barrera frente a documentos inválidos o arrays fuera de límite.

La ejecución periódica será un bean de infraestructura configurable por intervalo y habilitación.
La operación explícita del job y la tarea periódica reutilizarán el mismo servicio para evitar dos
semánticas de checkpoint.

### Índices y validación

`CatalogIndexInitializer` creará de forma explícita el índice único de `sku` y el compuesto
`category._id, price`; no se crearán índices de atributos porque no hay filtros de atributos.
También aplicará el validador `$jsonSchema` para campos obligatorios, `price`, límites de arrays y
tipos esperados. La configuración de Mongo desactivará `spring.data.mongodb.auto-index-creation`.
El inicializador será idempotente y se ejecutará durante el arranque del modo que necesite Mongo.

### Reglas de arquitectura

ArchUnit comprobará, como mínimo:

- Las clases Mongo, documentos, repositorios de Spring Data Mongo, inicializadores y mappers solo
  aparecen bajo `catalog.infrastructure`; `domain` y `application` no importan Spring Data Mongo,
  BSON ni tipos de infraestructura.
- `catalog.application` depende únicamente de `catalog.domain`; no depende de `catalog.infrastructure`.
- Los puertos de salida permanecen en `catalog.domain.port` y sus adaptadores Mongo/JPA permanecen
  en `catalog.infrastructure.adapters.out`.
- `catalog.infrastructure.config` es el único lugar que ensambla beans; no se usa
  `ApplicationContext.getBean()` ni acceso estático a beans.
- `catalog` no depende de `inventory`; `availableStock` se obtiene como dato de lectura derivado y
  no introduce una dependencia de código hacia ese módulo.
- Los DTO web y documentos JPA/Mongo no contienen conversiones hacia dominio; las conversiones
  viven en mappers de infraestructura.

### Buscador, suite dual y paridad

La búsqueda Mongo implementa texto «contiene, sin mayúsculas» como regex con `Pattern.quote`, para
que `.` y `(` sean literales. El orden por `name` usa una collation/criterio equivalente a
PostgreSQL, probado con mayúsculas, minúsculas y acentos. La paridad con `Collation.simple()` exige
collation `C` en PostgreSQL (orden por punto de código), o una collation de idioma en MongoDB
equivalente a la configurada en PostgreSQL. El conteo y la página son dos operaciones: una de
`count` y otra de página.

La suite HTTP común se organiza en las clases base `ProductSearchIT`, `ProductDetailIT` y
`ProductReviewQueryIT`. La base expone `afterWrite()` y la subclase Mongo ejecuta ahí la migración.
El escenario `Producto recién creado visible en el catálogo` demuestra el desfase aceptado: el
producto escrito en PostgreSQL no se ve en Mongo antes de la migración y sí después del hook. Los
tests guardianes de `scripts/protected-tests.txt` no se mueven ni modifican.

Como `@ConditionalOnProperty` deja un solo adaptador de lectura por contexto, el test de paridad
instancia ambos explícitamente: JPA con `EntityManager` y Mongo con `MongoTemplate`. Compara los
objetos `ProductDetail` y `ProductSearchPage` de dominio, no JSON.

### Pruebas con Testcontainers

Se levantará PostgreSQL para preparar productos, variantes, atributos, stock y reseñas; el modo
Mongo levantará además MongoDB y ejecutará la migración antes de invocar la suite HTTP. La suite
base abstracta contendrá los escenarios de buscador, ficha y reseñas, y habrá una subclase para
cada valor de `catalog.read-store`.

Las pruebas de persistencia inspeccionarán en MongoDB los índices, el validador, límites de arrays,
recuentos, checkpoint, keyset e idempotencia. La equivalencia comparará 200 SKU aleatorios y el
promedio de rating por `compareTo`. Un `CommandListener` del driver en la prueba contará comandos
`find` y `aggregate`: la ficha debe costar exactamente un comando con 60 reseñas, 60 variantes y 60
atributos; las reseñas completas permanecen en su colección separada.

Los tests existentes que no activan `catalog.read-store=mongo` ni `catalog.migration.enabled=true`
no requieren MongoDB: el driver conecta de forma perezosa y
`spring.data.mongodb.auto-index-creation=false` evita conexiones de auto-indexación al arrancar.
`MasterAcdcApplicationIT` verificará ese arranque sin Mongo disponible. No se excluye la
auto-configuración de Mongo en contextos PostgreSQL: cuando la migración está habilitada, esa
infraestructura debe existir aunque PostgreSQL siga siendo el adaptador lector.

## Risks / Trade-offs

- [Desfase entre PostgreSQL y MongoDB] -> Mantener `postgres` por defecto, checkpoint observable,
  informe de cada ejecución y pruebas de recuento/paridad antes de activar `mongo`.
- [Fallo durante un lote deja una copia parcial] -> Confirmar el checkpoint solo después del upsert
  del lote y hacer cada operación repetible; reanudar desde el último UUID confirmado.
- [Cambios de categoría o stock no visibles inmediatamente] -> Re-migración periódica configurable;
  aclarar que `availableStock` solo sirve para el indicador de catálogo y no para reservar stock.
- [Dos almacenes aumentan coste operativo] -> No eliminar JPA en este change y conservar rollback
  inmediato cambiando `catalog.read-store` a `postgres`.
- [La migración debe operar mientras se lee desde PostgreSQL] -> Activar la infraestructura Mongo
  por `read-store=mongo` O `migration.enabled=true`; los tests con ambas propiedades ausentes
  arrancan sin Mongo por conexión perezosa y auto-indexación desactivada, comprobado con
  `MasterAcdcApplicationIT`.
- [Arrays o documentos inválidos rompen la migración] -> Validación `$jsonSchema`, límites en el
  mapeo y pruebas de rechazo antes de aceptar un lote.

## Migration Plan

1. Añadir la infraestructura Mongo, el validador y los índices sin cambiar el valor por defecto.
2. Preparar un PostgreSQL y MongoDB de integración, ejecutar una migración completa y comprobar
   recuentos, fichas y reseñas.
3. Activar la suite API en ambos modos; el modo Mongo migra los datos preparados antes de leer.
4. Desplegar con `catalog.read-store=postgres`, habilitar el job periódico y observar checkpoints,
   informes y desfase.
5. Cambiar a `catalog.read-store=mongo` solo tras validar paridad. Rollback: volver a `postgres`;
   no se borran documentos ni tablas.

## Open Questions

No quedan preguntas abiertas que cambien el contrato, la arquitectura o el desglose de tareas.
