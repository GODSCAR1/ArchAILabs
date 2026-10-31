# Design

## Context

El catálogo hoy solo crea productos y su persistencia no contiene categoría, estado ni fecha de creación. Inventory almacena una fila por producto; el listado debe leer ese dato en la misma base de datos, sin llamadas a la API transaccional de inventory por resultado. La consulta debe seguir el patrón del ADR de listados y mantener un coste de consultas independiente del número de filas.

## Goals / Non-Goals

**Goals:**

    - Implementar una consulta de catálogo como proyección paginada, con filtros parametrizados y ordenación validada mediante una lista cerrada.
- Resolver categoría y disponibilidad en la lectura, tratando la ausencia de stock como cero.
- Mantener separadas las capas hexagonales y limitar el acoplamiento del catálogo al mapeo de stock de solo lectura.
- Migrar el esquema y los datos existentes de forma desplegable y reversible.

**Non-Goals:**

- Convertir el catálogo en dueño de las reglas de reserva.
- Compartir entidades persistentes entre módulos.
- Crear una API HTTP adicional para consultar inventory.

## Decisions

### Proyección paginada y estrategia de consulta

Cada filtro se implementará como una `Specification` independiente sobre el metamodelo estático `ProductJpaEntity_` y se combinarán con `Specification.allOf`; nunca se construirá JPQL concatenando valores del usuario. La consulta unirá la categoría y el stock para producir el resumen, tratará un stock ausente como cero y añadirá siempre id al orden.

La elección final queda entre dos alternativas y se decidirá en `docs/adr/0007` con números medidos:

- A: `Specification` con fetch de categoría y stock en la consulta de página, excluyendo esos fetches en la consulta de count, con un máximo esperado de dos consultas por página.
- B: Entity View de Blaze-Persistence, con una única consulta de proyección si las mediciones confirman que cubre filtros, orden, count y stock sin regresiones.

No se invocará la API pública de inventory por producto. El stock se mapeará como entidad de solo lectura con `@Immutable` dentro de `catalog.infrastructure.adapters.out.persistence`, sobre la tabla compartida, y se usará únicamente para el join de consulta. Este mapeo no permite escrituras desde catalog y no se expone como entidad de dominio.

### Modelo de producto y categorías

Se añadirá una categoría persistente con UUID, nombre y versión, y la persistencia del producto tendrá `categoryId`, `status`, `createdAt` y `version`. La versión se gestionará únicamente en las entidades JPA mediante `@Version`; no se añadirá al modelo de dominio. La relación se resolverá en la proyección para obtener `categoryName`; no se expondrá una entidad ni se usará una asociación EAGER. Los estados serán un enum de persistencia con `DRAFT`, `ACTIVE` y `DISCONTINUED`.

La migración creará primero categorías y columnas nuevas. Para productos existentes, asignará una categoría inicial `Sin categoría`, marcará el estado como `ACTIVE`, establecerá `createdAt` con la fecha de migración y `version` en `0`; después podrá imponer las restricciones de no nulo que el modelo requiera. Las filas existentes conservan id, nombre, SKU y precio. Las categorías nuevas empezarán con `version=0`.

La migración se hará en una versión Flyway posterior a las actuales, con índices para categoría, estado, precio y fecha según el plan de consultas. No se añadirá un índice sobre `lower(name)`: la búsqueda contiene texto con `LIKE '%x%'`, que no se beneficia de un índice B-tree convencional.

### Errores HTTP y parámetros

La capa web validará `minPrice > maxPrice` y el campo de orden antes de ejecutar la consulta, traduciendo cada caso a un `ProblemDetail` con un `type` estable y distinto. `size` se normalizará al máximo 100; no se tratará como error por superar ese límite. Los valores de estado y UUID inválidos seguirán el manejador de parámetros existente y deberán conservar respuesta RFC 9457.

### Creación de productos

Después de la migración, la creación de un producto asignará la categoría `Sin categoría`, el estado `ACTIVE` y `createdAt` al instante de creación. `CreateProductIT` verificará esos valores directamente en la fila persistida; los tests del listado verificarán por separado que el producto aparece con esos valores.

## Reglas de arquitectura

- La regla `catalogDoesNotDependOnInventory` SHALL impedir que cualquier paquete de catalog importe clases de inventory.
- La regla `inventoryDoesNotDependOnCatalog` SHALL impedir que cualquier paquete de inventory importe clases de catalog.
- El mapeo inmutable de stock dentro de catalog.infrastructure queda exento de la futura regla que exige `@Version` en las entidades `@Entity` de catalog, porque es una representación de solo lectura de una tabla cuyo ciclo de vida y escritura pertenecen a inventory.
- La composición de beans y la implementación de adaptadores se mantendrán en infraestructura.

## Risks / Trade-offs

- [Riesgo] El `left join` puede duplicar filas si el modelo de categoría o stock deja de ser uno-a-uno → Mantener unicidad por identificador y probar totales y conteo de consultas con datos reales.
- [Riesgo] La fecha de migración hace que productos históricos tengan el mismo `createdAt` → El desempate por id mantiene resultados deterministas; no se inventa precisión histórica.
- [Riesgo] El read model comparte almacenamiento con inventory y puede acoplar despliegues → Limitar el acoplamiento al mapeo `@Immutable` de solo lectura, protegido por `catalogDoesNotDependOnInventory` e `inventoryDoesNotDependOnCatalog`; no exponer escrituras cruzadas.
- [Riesgo] La búsqueda `LIKE '%x%'` no usa un índice B-tree convencional → Medir su coste con el volumen objetivo y decidir en una evolución separada si se necesita una estrategia de búsqueda distinta.

## Migration Plan

1. Añadir la migración que crea categorías, añade columnas, rellena productos existentes y crea índices.
2. Desplegar el código que puede leer los nuevos campos y ejecutar la consulta solo después de que Flyway haya finalizado.
3. Verificar con PostgreSQL/Testcontainers productos antiguos, productos sin stock, páginas vacías y límites de tamaño.
4. Para rollback, retirar el endpoint/código de consulta y restaurar la versión anterior de la aplicación; no ejecutar una migración destructiva automática. Si fuera necesario revertir el esquema, hacerlo con una migración explícita revisada que preserve los datos originales.
