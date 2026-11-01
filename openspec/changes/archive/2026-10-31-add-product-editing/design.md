# Design

## Context

El catálogo ya persiste una columna de versión optimista y usa puertos hexagonales para creación y búsqueda, pero el modelo de dominio no expone todavía esa versión ni existe un puerto de edición. La creación mantiene conversiones en la entidad, el controlador y la respuesta, mientras que el buscador ya concentra sus conversiones en mappers dedicados. Ver `proposal.md` para la motivación y `specs/product-editing/spec.md` para el contrato observable.

## Goals / Non-Goals

**Goals:**

- Modelar la versión como parte del resultado de creación y edición.
- Separar lectura de versión, validación previa y guardado con control optimista.
- Traducir tanto el rechazo por versión obsoleta como el conflicto descubierto en flush al mismo error HTTP.
- Mantener la conversión de cada frontera en un único mapper dedicado.
- Mantener la transacción fuera de domain y application.

**Non-Goals:**

- Cambiar el esquema funcional de categoría, status o ordering.
- Añadir bloqueo pesimista, detalle de producto o una nueva dependencia.

## Decisions

### Puerto y flujo de edición

Añadir un caso de uso de edición y las operaciones mínimas de salida para localizar un producto por id, comprobar su versión y guardar el producto editado. El servicio construirá `ProductName` y `Price` antes de modificar el agregado, comparará la versión esperada con la actual y devolverá un producto con la versión incrementada tras guardar. La inexistencia y la versión obsoleta serán excepciones de dominio diferenciadas para que el adaptador web mantenga el contrato estable.

La entidad de dominio incorporará la versión persistida; la creación comenzará en la versión inicial y la actualización conservará categoría, status, SKU y fecha de creación. El adaptador de persistencia copiará la versión con `ProductPersistenceMapper`, que también será el único lugar para convertir la entidad JPA a dominio y viceversa. `ProductJpaEntity.from` dejará de hacer esa conversión.

### Transacción y concurrencia

La configuración de infraestructura envolverá el caso de uso de edición con `TransactionTemplate`, siguiendo el patrón existente en ordering. El servicio de aplicación seguirá siendo independiente de Spring y no llevará `@Transactional`.

El adaptador de persistencia guardará con `saveAndFlush`. Se conservará `@Version` como mecanismo de segunda barrera: la comparación explícita da un rechazo claro antes de mutar cuando el cliente ya está obsoleto, y el control optimista del flush cubre la carrera entre lectura y confirmación. El adaptador capturará `ObjectOptimisticLockingFailureException` y la traducirá a la excepción de dominio de concurrencia, de modo que el manejador web publique HTTP 409 con `https://api.master-acdc.dev/problems/concurrent-modification` sin depender de que el flush ocurra al hacer commit de `TransactionTemplate`.

Se elige `@Version` frente a bloqueo pesimista porque los conflictos de edición humana son raros y el usuario puede tardar mientras edita. No se deben retener locks durante ese intervalo; el control optimista permite detectar únicamente la colisión al confirmar.

### Mapeo web y compatibilidad de respuestas

`ProductWebMapper` concentrará la conversión de peticiones de edición a criterios/comandos y de productos a respuestas. La respuesta de creación reutilizará ese mapper y añadirá la versión sin conservar `CreateProductResponse.from`. El controlador solo coordinará request, caso de uso, código HTTP y ubicación.

### Reglas de arquitectura

- `domain` no dependerá de Spring, JPA ni Jackson.
- `application` dependerá únicamente de `domain` y de sus puertos; no dependerá de `infrastructure`.
- Los puertos nuevos vivirán en `domain/port` y sus implementaciones en `infrastructure/adapters/out`.
- Los casos de uso tendrán interfaz en `application/port` y servicio en `application`.
- La demarcación con `TransactionTemplate` y el ensamblado de beans vivirán en `infrastructure/config`.
- Los mappers web y de persistencia serán clases `final`, sin estado y con métodos estáticos; domain y application no los conocerán.
- No se modificará la dependencia entre ordering e inventory: inventory no dependerá de ordering y ordering seguirá usando su API pública.

### Documentación de deuda

Actualizar `docs/lab-s09.md` para eliminar de "Deuda: mappers" las entradas de creación del catálogo. La única deuda restante en esa sección será la conversión de ordering.

## Risks / Trade-offs

- [El conflicto puede producirse después de la comprobación explícita] → Mantener `@Version`, usar `saveAndFlush` dentro de la transacción y traducir `ObjectOptimisticLockingFailureException` al error 409 antes del commit.
- [La versión del dominio puede quedar desincronizada si se fabrica el resultado antes del flush] → Leer la versión generada por la persistencia tras guardar y cubrirlo con una prueba de integración que compruebe la versión devuelta.
- [El refactor de mappers puede alterar respuestas existentes] → Conservar los campos y normalizaciones actuales y ampliar las pruebas de creación y de los mappers.

## Migration Plan

No se requiere migración de base de datos: la versión ya existe. Desplegar el cambio de forma compatible para `POST` añadiendo el campo `version`; habilitar el nuevo PUT junto con sus manejadores de error. Para rollback, retirar el endpoint y los servicios nuevos sin modificar datos estructurales; los productos creados conservarán la versión ya persistida.
