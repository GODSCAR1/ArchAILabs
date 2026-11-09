# Design

## Context

Véase `proposal.md` para la motivación. Estado observado en `lab/s12`:

- `UserAccount.Profile` y la persistencia ya llevan `long sessionVersion` no negativo. `JwtTokenAdapter.issue(UserAccount)` omite ese dato; no hay que cambiar la firma del puerto emisor.
- `LoginService` relee con `UserAccounts.findForUpdate` después de verificar el hash; emite con esa cuenta actual. `RefreshTokenAccess` bloquea primero cuenta y después token; `RefreshService` comprueba enabled y emite con la cuenta bloqueada. Se conservan esos locks y la revalidación de contraseña.
- `ChangePasswordService` guarda otro hash conservando el perfil y revoca todas las familias. `SessionConfiguration` envuelve los casos de uso en `IdentityTransactions`/`TransactionTemplate`, con lock_timeout de 2 s. El incremento debe entrar en esa misma unidad de trabajo.
- `JwtDecoderFactory` valida RS256/kid, issuer, audience y tiempos; `JwtConfiguration` lo ensambla. `HttpSecurityConfiguration` ya exige ADMIN para `/api/admin/**`; falta el permiso de gestión en el decorador.
- Redis ya está instalado: command/connect timeout de 200 ms en propiedades; `REJECT_COMMANDS` de Lettuce está condicionado a `catalog.cache.enabled=true`. Identidad debe mantener el fallo rápido aunque catálogo desactive su cache.
- Se han leído todos los ADR presentes en `docs/adr/` (0007, 0009, 0010), las cuatro specs de seguridad/identidad relevantes, `docs/caching.md`, el paso 6 y el listado de guardianes. ADR 0011 se menciona en documentación pero no existe aún en `docs/adr/`; el paso 8 sigue siendo humano.

## Goals / Non-Goals

**Goals:** una comprobación compartida por autenticación, revocación efectiva antes del 204 y un protocolo de fallo conservador sin transacción distribuida. Distinguir errores de autenticación (401) de comandos ya autenticados que no logran establecer la barrera (503).

**Non-Goals:** L1, pub/sub, reutilizar la cache de productos, cambios en modelos de pedidos/inventario, TTL nuevos, validación de versiones dentro de cada servicio de negocio o garantía de cancelar peticiones que ya se autenticaron antes de la revocación.

## Decisions

### 1. Ocho claims y validador obligatorio

`JwtTokenAdapter` añade únicamente `sv=account.profile().sessionVersion()`. Login y refresh siguen tomando la cuenta bajo el lock existente, sin lectura Redis para decidir qué versión emitir. Se mantienen firma, kid, 900 segundos, roles y jti. Los deltas sustituyen íntegramente los requirements de claims de authentication/authorization y sus dos escenarios de claims exactos.

`SessionVersionValidator implements OAuth2TokenValidator<Jwt>` vive en infraestructura de seguridad y se añade obligatoriamente a la cadena de `JwtDecoderFactory`; no sustituye ninguno de sus validadores actuales. La factoría recibe la dependencia explícita; actualizar sus callers de test, sin sobrecarga que omita la validación en producción. Validar el valor crudo: número entero, no negativo y representable exactamente como long; no `getClaimAsString` ni `Number.longValue()` con truncamiento. Rechazar cadenas, booleanos, fracciones y overflow; sub inválido o cuenta ausente también falla.

El validador convierte indisponibilidad concreta del puerto a `OAuth2TokenValidatorResult.failure(invalid_token)`; la frontera HTTP existente produce 401 `unauthorized` con Bearer y ProblemDetail. WARN con mensaje fijo y categoría de fallo, sin token, UUID de usuario, email, claves Redis ni mensajes/stacktraces que puedan contener datos. El mismo criterio se aplica a errores de población o corrupción. No se capturan Exception/RuntimeException/Throwable ni se aceptan tokens por error.

Alternativa descartada: filtro propio que duplique el decoder o validador opcional por propiedad. Permitiría bypasses y duplicaría el contrato HTTP.

### 2. Almacén Redis separado y carga serializada

Puertos mínimos en `identity.domain.port`: lectura de versión vigente y publicación de barrera; sus adaptadores no exponen Spring. Un record de dominio inmutable representa la versión. Infraestructura combina Redis y `UserAccounts` mediante un componente transaccional ensamblado en `identity.infrastructure.config`; el dominio/aplicación nunca importa Redis, OAuth o transacciones.

Fila que se añadirá a `docs/caching.md` antes del código, una vez aprobada la excepción:

| Cache/control | Clave | L1 | L2 | Estrategia | Quién actualiza |
|---|---|---|---|---|---|
| identity session version | `shop:identity:session-version:v1:<UUID>` | ninguna | Redis sin expiración | lectura por petición; cache-aside con lock de cuenta en miss | cambio de contraseña y block publican la versión futura antes del commit; miss publica la versión persistente bajo el mismo lock |

El valor wire es un entero decimal validado; no contiene cuenta, roles ni secretos. Este prefijo no pertenece al barrido `shop:cache:v1:` de productos. No usar `@Cacheable`, `CacheErrorHandler` de catálogo ni fallback fail-open. No hay TTL ni cambio en `CacheTtlPolicy`: una barrera adelantada por rollback no debe caducar automáticamente. La excepción propuesta incluye reglas genéricas de `@Cacheable(sync=true)`, ProductDetail, jitter y AFTER_COMMIT, no una modificación de las caches existentes. Las claves de identidad requieren retención sin eviction; errores de capacidad son fallos cerrados, nunca expulsión de barreras.

Hit: exactamente un GET Redis y comparar. Miss: abrir `IdentityTransactions`, adquirir el mismo lock pesimista de cuenta, volver a consultar Redis y, si sigue ausente, escribir la versión PostgreSQL con SETNX antes de soltar el lock. Si otro escritor ya pobló, usar ese valor, sin sobrescribirlo. Cuenta inexistente se rechaza sin poblar. Error de GET/SETNX o de lectura/lock de origen se traduce en rechazo 401, no en autenticación parcial. El timeout de lock de 2 s sigue vigente; los 200 ms son por operación Redis, no un SLA de toda la reconstrucción.

La segunda lectura en miss es deliberada: evita reducir una barrera publicada mientras se esperaba el lock y serializa también misses concurrentes de distintas instancias. No basta con leer BD sin lock y SETNX: una lectura antigua puede poblar después de una revocación y una expulsión de clave.

### 3. Publicación previa al commit: protocolo y prueba de seguridad

Todos los escritores de contraseña/estado y todos los populadores de miss usan el lock de cuenta. Orden del comando:

1. Obtener lock de cuenta; comprobar existencia, enabled cuando proceda, contraseña actual y política antes de tocar Redis. Preparar `n+1` con incremento exacto sin overflow.
2. Guardar en la transacción local el nuevo perfil (versión n+1 y enabled=false para block, o nuevo hash para contraseña) y revocar familias. No confirmar todavía. El flush de JPA no es commit.
3. Publicar sin expiración una barrera de al menos n+1 en Redis mientras se retiene el lock. Una operación atómica de comparación/publicación conserva el máximo entre valor existente y n+1 y rechaza datos corruptos. Esperar confirmación. Si falla, lanzar una excepción concreta de disponibilidad que cause rollback; no devolver 204 ni ejecutar compensación hacia n.
4. Confirmar PostgreSQL; responder 204 solo tras commit. No existe escritura Redis necesaria después del commit.

La publicación antes de commit es el punto desde el cual cualquier validación posterior ve la versión nueva o rechaza por Redis indisponible. La BD sigue siendo la verdad del estado confirmado y de la versión que se emite; Redis adelantado es una barrera conservadora, nunca una autorización para emitir esa versión. Login/refresh no pueden emitir n+1 antes del commit porque esperan el lock. Una petición autenticada antes de la barrera puede terminar: revocación inmediata no cancela trabajo ya autorizado.

Un timeout no cancela necesariamente el comando remoto. Por eso no basta un SET ciego bajo lock: una escritura incierta podría ejecutarse después de otro escritor. Usar un script Redis de una sola clave que compare enteros decimales canónicos por longitud/orden lexicográfico (sin `tonumber` que pierde precisión de long) y solo avance la barrera. Los rellenos usan SETNX, no SET. Probar explícitamente escritura retrasada de n/n+1 después de publicar n+2: nunca debe rebajar la clave. No borrar ni expulsar claves con comandos en vuelo; la recuperación vacía requiere cerrar conexiones previas y coordinar tráfico, como en Migration Plan.

| Fallo/intercalado | Resultado seguro |
|---|---|
| Validación/política/lock antes de publicar | No cambia estado ni versión; se conserva el error de negocio/contención existente. |
| SET falla y no se aplicó | Rollback de hash/enabled/versión/familias; comando 503. No hubo revocación confirmada: el estado anterior permanece. Redis caído implica 401; al recuperarse sin escritura aplicada, siguen siendo sesiones del estado anterior, no tokens de una revocación confirmada. |
| SET se aplicó pero se perdió la respuesta | Rollback PostgreSQL y 503; Redis n+1 permanece, rechazando incluso más sesiones. Nunca restaurar n como compensación. |
| SET confirmado y falla el commit | 503 ante fallo de almacenamiento; Redis n+1 queda como barrera, incluso si BD revierte a n. No bajar Redis automáticamente ni poblarlo en cada login. |
| Commit se completó pero se perdió su confirmación | Cliente puede recibir 503; Redis ya tiene n+1 y BD también, por lo que ningún bearer n vuelve a aceptarse. |
| Proceso termina tras SET y antes de commit | PostgreSQL revierte; queda barrera conservadora n+1. Si luego la clave se pierde, una carga bloqueada solo puede leer el estado realmente confirmado de BD. |
| Miss antes que revocación | Publica n bajo lock y termina; después el comando publica n+1 antes de su commit. |
| Revocación antes que miss | El miss espera el lock y no publica n; ve n+1 compartido o carga n+1 confirmado si la clave desapareció. |
| Redis se reinicia vacío de forma coordinada | Miss bloqueado reconstruye desde BD; tras revocación confirmada solo publica n+1 o superior. Las conexiones del proceso anterior se cierran antes de reabrir tráfico. |
| Escritura incierta llega tarde | Publicación monótona/SETNX impide bajar o sobrescribir una barrera ya existente. |

Invariante: **ninguna revocación confirmada puede dejar una clave antigua que acepte sus tokens anteriores**. No se promete que un comando que falla sin modificar ninguno de los dos almacenes haya revocado nada; no se informa éxito. Cuando sí queda un efecto parcial (barrera publicada), favorece la denegación. Documentar este límite evita confundir rollback con una revocación realizada.

Tras un rollback con barrera adelantada, login puede emitir n de BD pero ese token se rechaza mientras permanezca n+1. Recuperación conservadora: repetir el comando autorizado para confirmar n+1; para contraseña puede ser necesaria intervención operativa si todas las sesiones quedaron cerradas. No implementar reconciliación automática a la baja. La persona debe revisar esta consecuencia de disponibilidad al aprobar el diseño.

Alternativas descartadas: commit BD y luego SET/evict (ventana de aceptación, y fallo deja cache antigua); solo evento AFTER_COMMIT/outbox (revocación eventual); DEL antes de commit sin carga bloqueada (reconstruye n); reutilizar la cache fail-open (acepta ante caída).

### 4. Administración y transacciones

Añadir interfaces block/unblock en `application.port`, servicios puros en `application`, decoradores proxificables de infraestructura con `@PreAuthorize` exigiendo `ROLE_ADMIN` y `user:manage`, y entrada POST sin cuerpo. La cadena `/api/admin/**` conserva su guardia de rol. El permiso se comprueba antes de parsear id/consultar objetivo, como el patrón de listado administrativo. No restringir block solo por rol ni poner anotaciones Spring en aplicación.

Block siempre guarda enabled=false, incrementa una vez por llamada y revoca todas las familias en la transacción del protocolo anterior. Unblock guarda enabled=true y conserva versión y familias, incluso repetido. No necesita escribir Redis para el objetivo porque no cambia la versión; su autenticación de actor sí depende de Redis. No se añade excepción de autobloqueo: ADMIN puede bloquearse y su bearer queda revocado después del 204. La matriz usa otro objetivo para no contaminar la siguiente celda.

Id inválido/inexistente autorizado: 404 `user-not-found`. Lock no disponible: 503 `authentication-busy`, Retry-After 1. Error de publicación o confirmación de almacenamiento: 503 `session-version-unavailable`, Retry-After 1. La traducción de fallos concretos de commit vive en infraestructura y no convierte indiscriminadamente errores de programación/integridad en disponibilidad. Si Redis falla durante la autenticación del actor, se devuelve 401 antes de llegar al comando; la prueba de escritura debe inyectar el fallo después de autenticar.

`ChangePasswordService` ya tiene cinco dependencias: si el nuevo puerto las supera, agrupar colaboradores en un record tipado y adaptar tests no protegidos; no `Object...` ni constructores legacy. Leer package-info antes de añadir clases. Mappers dedicados para cualquier conversión nueva, sin conversiones en controller, entidad o DTO.

### 5. Inventario de tests y estrategia de compatibilidad

| Punto observado | Impacto y estrategia propuesta |
|---|---|
| `identity/AuthHttpSupport.signedToken` → `AuthFlowIT` | Firma fuera de AccessTokenIssuer, sub aleatorio y siete claims. Usar cuenta fixture persistida y sv vigente por defecto; eliminar/sustituir solo sv para casos negativos, conservando correctos los demás campos. Un 401 por cuenta inexistente no prueba audiencia, issuer o sv. |
| `identity/infrastructure/adapters/out/security/JwtTokenAdapterTest` | Re-firma claims con Nimbus y llama directamente a JwtDecoderFactory. Añadir dependencia explícita de versión controlada en unitarios y ocho claves exactas; conservar tests de firma/kid/tiempos y roles. Añadir pruebas específicas del validador, sin un decoder alternativo permisivo. |
| `RolesAndPermissionsConverterTest` | Construye Jwt directamente sin decoder. Sigue siendo unitario de conversión, no evidencia de sv; incluir el nuevo permiso ADMIN y no atribuirle cobertura de revocación. |
| `ProductControllerTest`, `ProductDetailControllerTest`, `OrderControllerTest` | Slices importan JwtConfiguration sin Redis/BD; jwt() omite decoder. Proveer `@MockitoBean` del puerto de versión/validador necesario para ensamblaje y conservar authorities de cada slice. No añadir Redis real para tests de DTO ni afirmar que jwt() prueba el validador. |
| `identity/infrastructure/config/JwtConfigurationTest` | Dos contextos mínimos con SpringApplicationBuilder solo para claves/decoder. Registrar dependencia de versión de test en ambos contextos y demostrar que el decoder contiene el validador obligatorio. No requieren Redis real para comparar claves. |
| `CatalogManagerToken` → `CreateProductIT`, `ProductSearchMongoIT`, `StockReservationIT` | Usan emisor real con cuenta NO persistida. Adaptar helper/callers no protegidos para persistir cuenta y emitir su versión real; añadir Redis real a sus contextos HTTP. No poblar Redis con identidades ficticias para eludir BD. |
| `OrderBearer` → `OrderPlacementIT`, `OrderCancellationIT`, `OrderDetailIT`, `OrderListingIT`, `OrderRefundSupport`/`OrderRefundIT`, `OrderAuthorizationQueryCountTest`, `SecurityMatrixSupport` | Persisten cuenta pero fuerzan perfil sv=0; leer la versión de la cuenta fixture, no resetearla. Contextos HTTP de ordering sin Redis necesitan contenedor. Precargar clave antes de reiniciar estadísticas para mantener presupuestos de queries. |
| `AdminUsersSupport` → `AdminUsersIT`, `AdminUsersQueryCountTest` | Ya heredan Redis, pero reutilizan UUID fijos y borran cuentas dentro de transacción. Limpiar exclusivamente claves de estas fixtures y precalentar la del actor antes de flush/clear/Statistics.clear. Conservar 120 cuentas, columnas permitidas y ≤2 queries; el miss de autenticación se mide aparte. |
| `IdentityIntegrationSupport`/`IdentityTestContainers` | PostgreSQL y Redis ya presentes para AuthFlowIT, ApiSecurityTest, SecurityMatrixTest, ActuatorSecurityIT, CurrentOrderActorIT, sesiones y persistencia. Extender soporte de fallo selectivo/limpieza; no parar el contenedor compartido y dejarlo caído para otros tests. |
| `SecurityMatrixTest`/`SecurityMatrixSupport` | De 14 a 16 endpoints: 80 celdas de matriz, más complementarios. Precalentar versión del actor antes de asserts de cero queries. Añadir cuenta/familias al snapshot; tratar explícitamente respuesta 204 sin parsear JSON vacío en assertSuccess. |
| `StockReservationConcurrencyTest` (GUARDIÁN) | Firma con emisor real para sub sin cuenta; contexto solo PostgreSQL. **Persona** añade cuenta persistida y Redis real/precalentamiento; mantiene 50 peticiones, 30 éxitos/20 insuficiencias, stock y umbrales del timeout. El agente se detiene antes de romperlo. |
| `CatalogProductEditingMongoIT`, `CatalogProductCacheEditingIT` (GUARDIANES) | jwt() de preparación/escritura puede evitar decoder. Persona revisa contexto y cobertura sin convertirlos en evidencia de revocación ni debilitar assertions de versiones/cache. El segundo ya declara Redis. |
| `ProductCacheMultiInstanceIT`, `CatalogStockProjectionCacheInvalidationIT`, `StockReservationTransactionIT` y demás guardianes | Invocaciones directas con AuthorizedCalls no pasan por decoder; el primero y segundo ya tienen Redis para catálogo. Revisar arranque de beans; no ampliar firmas protegidas ni añadir un modo de producción sin sv. |

Contextos completos sin Redis declarado que no autentican bearer no deben adquirirlo solo para arrancar beans: `MasterAcdcApplicationIT`, `OrderSummaryProfilingIT`, `OrderTransactionIT`, `OrderSummaryQueryAdapterIT`, `OrderRepositoryAdapterIT`, `InventoryFacadeIT`, `InventoryConcurrencyIT`, `InventoryReservationComparisonIT`, `InventoryRepositoryAdapterIT`, `ProductSearchQueryIT`, `ProductDetailMongoIT`, `ProductReviewQueryIT` y `CatalogStockProjectionSchedulingIT`. Usar inyección sin I/O en constructores/@Bean; comprobar que siguen arrancando. Los contextos de cache con Redis propio conservan sus conexiones. El puerto debe poder ensamblarse en aplicación completa sin un GET en el arranque; en slices mínimos se suministra explícitamente el doble de test. No hay fallback global de test que desactive el control.

La lista `scripts/protected-tests.txt` es la autoridad para detener cualquier edición de guardianes, incluso si una adaptación de soporte compartido hace fallar uno no previsto. No tocar la lista, hooks, plugin, configuración de Maven ni archivos application protegidos. La protección adicional del paso 8 se agenda solo para la persona después del cambio.

### 6. Evidencia de seguridad, carreras y coste

- `AuthFlowIT`: contraseña con dos dispositivos → access anteriores 401 inmediato y refresh de ambos 401; nuevo login/refresh llevan sv nuevo; block → bearer vigente 401, login/refresh bloqueados; unblock no resucita nada. Claims exactos en login y refresh, manteniendo ambos DisplayName vigentes.
- Casos firmados reales: sv ausente, `"0"`, otro texto, booleano, objeto, array, fracción, negativo, overflow, versión menor/mayor; probar solo una variable inválida por caso y cuenta existente. Los tests unitarios fijan además semántica de tipos sin truncamiento.
- Redis real con Testcontainers: lectura por conexión caída y timeout (conexión aceptada pero comandos retenidos), población fallida, valor corrupto, SET no aplicado, SET aplicado con respuesta perdida, fallo PostgreSQL después de SET, miss contra revocación, y login/refresh concurrentes con contraseña/block. Barreras/latches en puntos concretos, no sleeps que oculten la carrera. Espías coordinan fallos y observan consultas reales; no sustituyen la BD para probar queries.
- Capturar logs de SessionVersionValidator: al menos WARN; no bearer, refresh, contraseña, email, UUID/clave ni cuerpos. Confirmar 401 ProblemDetail/challenge y negocio intacto. Para escritura mantener sano el GET del actor e inyectar fallo únicamente al SET del objetivo.
- `SessionVersionQueryCountTest` en fast loop, PostgreSQL+Redis reales, 120 pedidos, flush/clear y Statistics.clear tras poblar clave: N peticiones producen N GET de versión, cero SELECT de versión y el presupuesto de negocio actual por petición (listado ≤2). Con clave ausente medir por separado una carga de origen y su población; las siguientes no vuelven a BD para versión. Contar comandos del prefijo de identidad, sin sumar tráfico de health/cache ni consultas de preparación. Conservar tests existentes de cero queries por denegación y columnas de listado administrativo.
- Medición de latencia reproducible: calentamiento, varias rondas con misma cuenta y conexión, `System.nanoTime`, p50/p95 de validación completa comparada con validación criptográfica/iss/aud/tiempo en un harness de benchmark aislado; ese baseline no es un bean de producción. Medir hit, miss y timeout por separado, indicando host/Docker, número de muestras y timeout 200 ms. Documentar delta en `docs/lab-s12.md`, sin umbrales de CI arbitrarios ni cifras inventadas. La prueba automatizada fija recuentos y fallo acotado, no una promesa universal de latencia.

### Reglas de arquitectura

Comprobar con `IdentityArchitectureTest`, `AuthorizationArchitectureTest` y `ModuleDependenciesTest`:

- `identity.application` depende únicamente de su dominio; `identity.domain` no importa Spring, Redis, OAuth, JPA o Jackson.
- Interfaces de salida en `identity.domain.port`; validación OAuth, adaptadores Redis/JPA, autorización y transacciones solo en infraestructura; `@Bean` solo ensambla colaboradores, sin protocolo de negocio incrustado.
- `identity.infrastructure` puede seguir consumiendo `ordering.api` para clientes; no se introduce dependencia inversa `ordering`/`inventory` → `identity` ni dependencia de estos módulos hacia cache/servicio de sesión.
- Mantener ausencia de ciclos y frontera de inventory existente sin ampliar las 15 violaciones congeladas; el umbral PIT de referencia sigue 75 %.
- Decoradores administrativos proxificables con permisos y rol exactos; no `@Transactional`/`@PreAuthorize` en dominio/aplicación; sin búsquedas de beans o estado estático.

## Risks / Trade-offs

- [Redis en el camino de toda autenticación] → Una lectura de red por petición; timeout/fallo rápido existente y medición obligatoria. Redis caído cierra API bearer, no transforma una caída en aceptación.
- [Barrera adelantada tras rollback deniega sesiones legítimas] → No compensar a la baja; respuesta 503 y recuperación operativa/reintento documentados. Es el coste explícito de priorizar seguridad.
- [Restaurar un snapshot Redis antiguo conserva una clave n tras commit n+1] → Redis de versiones es estado derivado: recuperación siempre vacía este namespace antes de servir tráfico, con conexiones anteriores cerradas; no leer réplicas atrasadas ni restaurar snapshots sin esa limpieza. Probar reinicio vacío. Un Redis que vuelve con datos antiguos sin reconciliar rompe la premisa y no se puede detectar con un único GET sin acudir siempre a BD.
- [Sin TTL, memoria proporcional a cuentas consultadas] → Una entrada pequeña por cuenta, sin historial; documentar mantenimiento coordinado con bajas/restauración, sin introducir un job ahora.
- [Cambiar infraestructura afecta guardianes y conteos] → Adaptación humana previa, claves calientes antes de medir y nada de bypasses o umbrales rebajados.
- [Reglas genéricas de cache incompatibles] → Aprobación y aclaración humana explícita antes de código; catálogo mantiene su política. Esta propuesta no concede permiso implícito para editar zonas protegidas.

## Migration Plan

1. Persona revisa protocolo y excepciones a reglas de cache; registra la decisión y adapta guardianes afectados. Registrar fila en `docs/caching.md` antes del código. El paso 8 y la eventual actualización de ADR/AGENTS/hooks/plugin quedan humanos.
2. Implementar y probar emisor+decoder+mutaciones en una misma entrega. Desplegar coordinadamente, sin mezclar emisores de siete claims con validadores de ocho ni instancias que bloqueen sin publicar barrera. Tokens previos sin sv pasan a 401; los usuarios necesitan obtener un par nuevo. No reescribir cuentas ni reiniciar session_version.
3. Usar Redis primario con retención sin eviction, prefijo separado y recuperación del namespace vacío desde PostgreSQL. Detener tráfico, cerrar conexiones con comandos pendientes, vaciar el namespace derivado y reabrir tráfico para reconstrucción serializada; no habilitar tráfico contra snapshots antiguos. Si la instalación compartida con catálogo no garantiza esta retención, la persona debe aprovisionarla antes del despliegue; no cambiar configuración protegida desde apply.
4. Ante problemas, retirar tráfico/roll-forward manteniendo el control; no volver a un decoder sin sv, porque resucitaría access tokens revocados. Mantener versiones persistentes y familias revocadas. Si hay que volver a una versión anterior, requiere invalidar material de firma/todos los tokens y revisión humana específica.
5. Tras comprobaciones y medición, persona aplica protección del paso 8 y sincroniza/archiva specs al revisar el PR. Nada de esto se ejecuta en el workflow de propuesta.
