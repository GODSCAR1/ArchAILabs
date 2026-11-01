# Design

## Context

Inventory ya expone una fachada transaccional para reservas de varias líneas y ordering usa una
actualización condicional atómica. El endpoint nuevo necesita otro mecanismo de concurrencia: debe
leer la cantidad disponible mientras mantiene el bloqueo de la fila del producto para devolver el
stock restante, distinguir stock insuficiente de registro inexistente y limitar la espera del
bloqueo. El contrato observable está en `proposal.md` y `specs/inventory/spec.md`.

La fila de stock contiene actualmente el identificador del producto y la cantidad disponible. La
ruta de reserva de inventory no consulta catalog, por lo que el registro de stock de inventory es
la fuente de existencia.

## Goals / Non-Goals

**Goals:**

- Serializar las reservas directas de un producto mediante un bloqueo pesimista de escritura.
- Validar la disponibilidad en el dominio después de adquirir el bloqueo.
- Devolver la cantidad restante persistida desde la misma transacción que reservó las unidades.
- Traducir la ausencia de stock y el timeout del bloqueo a errores de dominio antes de que el
  adaptador web los convierta en `ProblemDetail`.
- Mantener disponible y sin cambios la ruta atómica de ordering para compararla.

**Non-Goals:**

- Introducir una entidad, un id, un flujo de liberación o una expiración de reservas, ni consultar
  catalog.
- Sustituir o modificar la operación `discountIfAvailable` de ordering.
- Hacer que domain o application dependan de Spring, JPA, Jackson o el adaptador web.

## Decisions

### Modelo de dominio y puerto

Añadir un modelo inmutable de balance de stock con `productId` y `available`, y una operación de
dominio que devuelva un balance nuevo tras reservar unidades positivas o lance la nueva
`InsufficientStockException` propia de `inventory.domain`, que transportará `available`. La
operación solo se llama después de adquirir el bloqueo, de modo que la comprobación y el descuento
formen una única transición de dominio y el resultado pueda devolverse al cliente.

Extender el puerto de salida de inventory con `findForUpdateById`. Devuelve el balance bloqueado
cuando existe y lanza `StockRecordNotFoundException` cuando no existe. Ninguna ruta de reserva
directa puede actualizar stock mediante la operación de actualización atómica existente: todas las
reservas directas deben pasar por esta lectura bloqueada. inventory no puede depender de
`ordering.domain.InsufficientStockException`.

### Bloqueo y timeout de persistencia

Añadir `StockJpaRepository.findForUpdateById` con `@Lock(PESSIMISTIC_WRITE)` y el hint JPA
`jakarta.persistence.lock.timeout=2000`. El adaptador de persistencia convierte la entidad
bloqueada al balance de dominio, guarda la cantidad decrementada y convierte el balance resultante
en el resultado de application.

PostgreSQL no admite la sintaxis `FOR UPDATE WAIT n`. No se afirmará por adelantado cómo Hibernate 7
traduce el hint estándar en PostgreSQL: el test de `Stock ocupado` debe verificar que la espera
efectiva está acotada y que la respuesta llega en torno a dos segundos. Si el test se cuelga o
supera aproximadamente los dos segundos, el plan B es ejecutar
`SET LOCAL lock_timeout = '2s'` en la misma transacción antes de `findForUpdateById`.

El adaptador debe reconocer el error PostgreSQL `55P03` (`lock_not_available`) y las excepciones
Spring `PessimisticLockingFailureException` o `CannotAcquireLockException` que lo envuelvan, y
traducir ese caso a `StockBusyException` sin ocultar fallos de persistencia no relacionados. El
test debe mantener la fila bloqueada desde otra transacción y demostrar el timeout real; dormir en
el código de aplicación no es una prueba válida.

### Límite transaccional de application

Añadir un caso de uso y un servicio de reserva de un producto que validen las unidades, carguen el
balance bloqueado, invoquen la operación de reserva del dominio y devuelvan producto, unidades y
disponibilidad restante. El caso de uso seguirá siendo independiente del framework.

Como en la fachada existente de inventory, ensamblar el límite transaccional con
`TransactionTemplate` en `inventory.infrastructure.config`. El controlador llamará al bean del
caso de uso transaccional; ni domain ni application usarán `@Transactional`.

### Adaptador web y contrato de errores

Situar el controlador en `inventory.infrastructure.adapters.in.web` aunque la ruta empiece por
`/api/products`. Añadir DTOs de petición y respuesta y un `StockReservationWebMapper` dedicado,
final, sin estado y con conversiones estáticas. El controlador solo debe convertir el UUID, invocar
el caso de uso, elegir HTTP 201 y devolver la respuesta mapeada.

El `@RestControllerAdvice` de inventory debe acotarse al controlador de reservas mediante
`basePackageClasses` o `assignableTypes`, y manejar únicamente `InsufficientStockException`,
`StockRecordNotFoundException` y `StockBusyException`:

- stock insuficiente: HTTP 409, type
  `https://api.master-acdc.dev/problems/insufficient-stock`, propiedad `available`;
- registro de stock ausente: HTTP 404, type
  `https://api.master-acdc.dev/problems/stock-not-found`;
- timeout del bloqueo: HTTP 503, type
  `https://api.master-acdc.dev/problems/stock-busy`, cabecera `Retry-After: 1`.

La validación de `units` ausente o no positiva mantiene el contrato web existente antes de invocar
el caso de uso. No se añadirán handlers genéricos.

### Evidencia de concurrencia y comparación del ADR

Crear tests deterministas con Testcontainers, `ExecutorService`, una barrera de salida con
`CountDownLatch` y un límite en cada `await` y `Future.get`. El test HTTP de concurrencia debe usar
`@SpringBootTest(webEnvironment = RANDOM_PORT)` y un cliente HTTP real, no `MockMvc` compartido
entre hilos. Lanzará 50 reservas de una unidad sobre 30 unidades y afirmará exactamente 30
respuestas HTTP 201, 20 respuestas HTTP 409, cero respuestas 503 y stock final cero.

Añadir un test separado que mantenga la fila bloqueada desde otra transacción, invoque la API y
afirme HTTP 503 con `Retry-After: 1` en torno a dos segundos y stock sin cambios.

Para el ADR 0007, ejecutar la misma carga de 50 reservas contra este endpoint pesimista y contra la
ruta existente `discountIfAvailable` de ordering. Registrar el tiempo total y las líneas de código
relevantes en la evidencia del ADR. El test comparativo no debe modificar la implementación de
producción de ordering.

### Reglas de arquitectura

- `inventory.domain` no depende de paquetes Spring, JPA, Jackson ni web.
- `inventory.application` solo depende de `inventory.domain` y sus puertos; nunca depende de
  `inventory.infrastructure` ni de `ordering`.
- El adaptador web de inventory depende hacia dentro del puerto de application y de los errores de
  domain, no de repositorios ni entidades JPA.
- Las implementaciones de persistencia permanecen en
  `inventory.infrastructure.adapters.out.persistence`, mientras que el ensamblado y
  `TransactionTemplate` permanecen en `inventory.infrastructure.config`.
- `inventory` no depende de `catalog`; el endpoint usa únicamente la identidad del stock de
  inventory.
- `ordering` puede seguir dependiendo de la API pública de inventory, pero inventory no puede
  depender de ordering.
- La conversión web y de persistencia permanece en mappers dedicados de infrastructure; los DTOs y
  las entidades JPA no exponen métodos de conversión.

## Risks / Trade-offs

- [Los bloqueos pesimistas serializan las reservas de un producto] -> Mantener la transacción
  limitada a bloqueo, comprobación de dominio, actualización y commit; usar la actualización
  atómica existente para ordering.
- [El hint JPA puede depender del dialecto] -> Verificar el comportamiento PostgreSQL con el test
  que mantiene el bloqueo y usar el plan B de `SET LOCAL lock_timeout` si no se obtiene un timeout
  real.
- [Los tests concurrentes pueden pasar accidentalmente porque empiezan en momentos distintos] ->
  Liberar todos los workers con `CountDownLatch`, limitar las esperas y afirmar el recuento de
  respuestas y el estado final de la base de datos.
- [Una reserva fallida podría dejar un decremento persistido] -> Mantener lectura, comprobación y
  actualización en una sola transacción y afirmar stock sin cambios en cada rechazo.

## Migration Plan

No se espera una migración de base de datos. Desplegar el endpoint nuevo de inventory y su
ensamblado sin cambiar ordering. El rollback consiste en retirar el endpoint y el caso de uso de
reserva directa; la ruta de reserva existente de ordering y el esquema de stock seguirán siendo
compatibles.
