# Lab S12 — evidencia de add-authentication

## Alcance y decisiones

El laboratorio se divide en tres changes revisables:

1. `add-authentication`: modelo, registro, claves, login, refresh, logout, cambio de contraseña y frontera HTTP de autenticación (pasos 1–4).
2. `add-rbac-and-object-authorization`: permisos, propiedad de recursos y matriz de autorización.
3. `add-session-version-revocation`: claim `sv` y revocación inmediata mediante versión de sesión.

Este documento recoge el primer change. Las secciones 5–9 al final quedan reservadas, sin evidencia de cumplimiento.

### Módulo identity y persistencia

- `identity` mantiene dominio y aplicación sin Spring, JPA ni Security. Los puertos se implementan en infraestructura; los casos de uso se ensamblan y envuelven con `TransactionTemplate` en configuración.
- La migración V8 añade cuentas y refresh en PostgreSQL. La cuenta enlaza un cliente mediante FK nullable y única; el registro crea cuenta y cliente atómicamente a través de `ordering.api.CustomerRegistrationFacade`.
- Registro normaliza el email, valida nombre opcional y contraseña (mínimo 12 caracteres, lista común y límite del encoder sin truncamiento), guarda `{bcrypt}` y devuelve 201 solo `{id}`, sin `Location` ni tokens. Login es una operación separada y usa hash de relleno para email desconocido.
- Solo se persiste SHA-256 del refresh opaco. Cada token vence en `min(emisión + 7 días, vencimiento de familia)`; la familia tiene un máximo absoluto de 30 días desde login.
- Las mutaciones bloquean primero la cuenta y después el token, con espera acotada. La reutilización confirma la revocación de toda la familia antes del 401. Con dos refresh simultáneos hay un ganador, un rechazo y el sucesor termina revocado. El timeout produce 503 con `Retry-After: 1` y rollback.

### JWT, claves y variables externas

- RSA 2048, RS256, `kid` derivado del thumbprint RFC 7638 y un único `JWKSource` para emisor y receptor. JWKS publica exclusivamente la clave pública.
- Access token de 900 segundos con exactamente `iss`, `sub`, `aud`, `iat`, `exp`, `jti`, `roles`; no incluye email, permisos, customerId ni `sv`.
- Issuer exacto `https://api.master-acdc.dev` (sin barra final) y audience `master-acdc-api`; se validan firma, algoritmo, `kid`, tiempos, issuer y audience.
- Producción necesita `SHOP_JWT_PRIVATE_KEY` (ruta a PEM PKCS#8 externo), `SHOP_OPS_USER` y `SHOP_OPS_PASSWORD_HASH` (hash delegado). Alimentan `shop.jwt.private-key`, `shop.ops.user` y `shop.ops.password-hash`. No hay secretos ni PEM empaquetados; `*.pem` queda ignorado por Git.
- `GlobalIdentityTestExtension` se autodetecta mediante el servicio JUnit. Genera una sola RSA 2048 por JVM, mantiene el PEM temporal fuera del repositorio hasta terminar la JVM y configura las propiedades de identidad/OPS antes de cargar los contextos. Los contextos completos y `SpringApplicationBuilder` usan el cargador de producción y comparten la clave; no requieren imports de configuraciones efímeras.

### Cadenas HTTP, cookies y CSRF

| Orden | Frontera | Política |
|---|---|---|
| 1 | Actuator (`EndpointRequest`) | Health público; resto Basic exclusivo de OPS con `ROLE_OPS`. Un JWT ADMIN no autentica operaciones. |
| 2 | `/.well-known/jwks.json` | GET público; otros métodos denegados. |
| 3 | `/api/**` | Stateless con bearer JWT. GET de productos/categorías y los cuatro POST exactos de register/login/refresh/logout son públicos; el resto exige autenticación. |

| Cookie | HttpOnly | Secure | SameSite | Path |
|---|---|---|---|---|
| `refresh_token` | Sí | Sí | Strict | `/api/auth` |
| `XSRF-TOKEN` | No | Sí | Strict | `/` |

`XSRF-TOKEN` tiene `Path=/` para que la SPA pueda leerla desde rutas fuera de `/api/auth`. Login materializa el token diferido. La SPA copia el valor crudo a `X-XSRF-TOKEN`; solo POST refresh/logout requieren cookie y cabecera CSRF. Registro, login y llamadas bearer no lo requieren. Un rechazo CSRF ocurre antes de consumir o revocar el refresh.

El `Max-Age` de `refresh_token` es el tiempo restante hasta su vencimiento real (`refreshExpiresAt` menos el reloj actual, con mínimo cero), no siete días fijos en cada rotación. Por tanto, una renovación en el día 29 no prolonga la cookie más allá del día 30 original. Logout borra la cookie con el mismo Path y atributos y `Max-Age=0`.

Los errores 401/403 usan ProblemDetail y conservan `WWW-Authenticate` Bearer/Basic cuando corresponde. Basic de OPS no autentica la API y una sesión HTTP no sustituye al bearer.

### Límite de revocación de este change

Logout revoca una familia; el cambio de contraseña revoca todas las familias del usuario atómicamente. Los access tokens ya emitidos conservan vigencia hasta `exp` (hasta 15 minutos desde su emisión). La revocación inmediata y el claim `sv` corresponden a `add-session-version-revocation`.

La API aún no aplica la matriz RBAC ni resuelve propiedad desde `sub`: pedidos conserva `X-Customer-Id` y un JWT válido cumple la precondición de autenticación de escrituras. El change de autorización completará esas restricciones.

## Intervención humana registrada

La persona confirmó realizadas 6.3 y 6.4 en el commit `0e718d0`:

- `CatalogProductEditingMongoIT` y `CatalogProductCacheEditingIT`: `jwt()` en preparación POST y operaciones PUT, conservando asserts de versiones y cache.
- `StockReservationConcurrencyTest`: bearer real para `HttpClient`, manteniendo 50 reservas, resultados 30/20 y umbrales de timeout.

Estas adaptaciones no son sustituidas por la extensión global de claves. Las tareas humanas 1.1 y 1.2 conservan su estado pendiente en el seguimiento; esta entrega no acredita su revisión ni el suministro de secretos de producción.

## Evidencia de integración y verificación

Los escenarios del delta se trazan con `@DisplayName` exactos en `AuthFlowIT`, sus escenarios HTTP heredados de `AuthHttpConcurrencyScenarios`, `ApiSecurityTest` y `ActuatorSecurityIT`. Se usan cadenas reales, bearer firmado y cookies/cabeceras CSRF reales en el flujo de autenticación. `RefreshTokenConcurrencyIT` y `RefreshTokenLockTimeoutIT` complementan la evidencia con transacciones y locks en PostgreSQL real.

Resultados de esta sesión (2026-11-08):

| Comprobación | Resultado |
|---|---|
| `bash ./mvnw spotless:apply` | Correcto; ningún archivo reformateado. |
| `openspec validate add-authentication --strict --no-interactive` | Change válido. |
| `bash ./verify-quality.sh --fast` | Correcto: specs activas 7/7, archivadas 11/11, trazabilidad de escenarios, reglas de persistencia/cache/guardianes, Spotless, Checkstyle y 243 tests sin fallos, errores ni omitidos. |
| `bash ./mvnw -q verify` | Correcto: Surefire 243 tests en 62 clases y Failsafe 196 tests en 39 clases; 0 fallos, 0 errores y 0 omitidos. |
| `graphify update .` | Actualizado: 2703 nodos, 5947 aristas. Advierte falta de parser SQL y dos archivos de configuración sin nodos; no acredita extracción SQL ni actualización semántica de esta documentación. |

En Failsafe pasan `AuthFlowIT` (27), `RefreshTokenConcurrencyIT` (1), `RefreshTokenLockTimeoutIT` (2) y `ActuatorSecurityIT` (4). Los guardianes humanos pasan: `CatalogProductEditingMongoIT` (7), `CatalogProductCacheEditingIT` (3) y, en Surefire, `StockReservationConcurrencyTest` (2). Los recuentos agregados solo incluyen informes generados en esta sesión, no informes históricos de `target`.

La tarea 7.2 queda cerrada con las comprobaciones anteriores en verde, por aclaración de la persona: la referencia a PIT era un arrastre y no forma parte del loop actual. PIT y Gitleaks no configurados en el proyecto; se incorporan cuando el loop los añada.

Salida completa conservada por el entorno de herramientas:

- Fast: `C:/Users/oscar/.local/share/opencode/tool-output/tool_1bd2f6cfc0012oJZO1dwa28dVU`.
- Maven verify: `C:/Users/oscar/.local/share/opencode/tool-output/tool_1bd302e4f001eHNgLo4nPMt24A`.

## 5. Permisos y propiedad

### Tabla RBAC de add-rbac-and-object-authorization

| Rol | Permisos |
|---|---|
| CUSTOMER | `order:create`, `order:read-own`, `order:cancel-own` |
| CATALOG_MANAGER | `product:write`, `stock:reserve` |
| SUPPORT | `order:read-any`, `order:refund` |
| ADMIN | `product:write`, `stock:reserve`, `order:read-any`, `order:refund`, `user:read` |

`Role` expone conjuntos inmutables. Los roles combinados suman permisos; ADMIN no
hereda CUSTOMER. El conversor del resource server deriva `ROLE_<nombre>` y permisos
solo de roles conocidos, sin tomar `scope`/`scp` como permisos. Un claim `roles`
ausente, no array o con elementos no textuales no concede authorities. Los nombres
desconocidos se ignoran sin eliminar los roles conocidos del mismo array.

La entrega inicial se detuvo antes de activar seguridad de método por llamadas
directas sin autenticación en guardianes adicionales a los previstos. En la
continuación solicitada por la persona se activan los decoradores y se registran
las incompatibilidades de esos guardianes, sin adaptarlos por el agente.

Evidencia parcial (2026-11-08): base de 439 tests, con fallo en
`RefreshTokenLockTimeoutIT.should_bound_account_lock_wait_and_roll_back_when_account_is_busy`
(1073 ms frente a mínimo 1500 ms). Tras 2.1–2.2 se ejecutan 469 tests: las pruebas
de roles/conversor/JWT y `AuthFlowIT` pasan, pero falla
`RefreshTokenLockTimeoutIT.should_release_account_lock_and_roll_back_when_token_is_busy`
(1078 ms frente a mínimo 1500 ms). Son métodos distintos: el segundo es un fallo
nuevo respecto a la lista base, aunque ambos pertenecen a la misma suite de timeout.
Se detiene apply sin corregir esa suite ajena a la sección 2. Los recuentos excluyen
informes históricos de `target`. La ejecución de inventario de fallos usa
`-Dmaven.test.failure.ignore=true` para completar Surefire y Failsafe; su código de
salida no acredita éxito. No se ha medido PIT ni alterado su umbral indicado del 75%.

### Continuación: fronteras de método (2.3–2.4)

La persona adaptó y formateó los guardianes de 1.2 y 1.3. Se ejecutaron antes y
después de activar seguridad de método: `CatalogProductEditingMongoIT` (7),
`CatalogProductCacheEditingIT` (3) y `StockReservationConcurrencyTest` (2) pasan
en ambas condiciones. El agente no cambió esos archivos.

`MethodSecurityConfiguration` activa `@EnableMethodSecurity` también fuera del
contexto web. Los únicos beans públicos de creación/edición de producto y reserva
directa son decoradores proxificables con `product:write`/`stock:reserve`, antes de
la frontera transaccional. El checkout interno conserva sus puertos sin ese permiso.
La cadena API añade `/api/admin/**` con `hasRole("ADMIN")`. `MethodAuthorizationTest`
prueba beans reales con permisos, sin permisos y sin Authentication, y observa los
repositorios para comprobar ausencia de delegación denegada. `ApiSecurityTest`
comprueba 403 ProblemDetail con bearer CUSTOMER real, rechazos administrativos y
contratos de CSRF/catálogo/versiones. Las fixtures no protegidas de integración
usan bearer firmado CATALOG_MANAGER; el slice usa authorities explícitas.

Verificación focalizada: 81 tests sin fallos. Suite completa: 479 tests, 10 fallos
por llamadas directas sin Authentication, en `ProductCacheMultiInstanceIT` (3),
`CatalogStockProjectionCacheInvalidationIT` (1), `CatalogStockProjectionSchedulingIT`
(1), `InventoryReservationComparisonIT` (1) y `StockReservationTransactionIT` (4).
Se detiene apply para revisión humana; no se declara la suite completa en verde.

### Problema abierto: RefreshTokenLockTimeoutIT (retomar al final del change)

La instrumentación temporal descrita a continuación se retiró por decisión humana:
`RefreshTokenLockTimeoutIT` vuelve al contenido del último commit y se elimina
`LockTimeoutDiagnostics`. Se conserva esta evidencia histórica, sin dar el problema
por resuelto ni modificar los umbrales. Se retomará al final del change.

Se instrumentó solo esta clase mediante un espía del DataSource y un observador
JDBC de test (`LockTimeoutDiagnostics`), sin cambiar SQL, parámetros, configuración
de timeout ni assertions. No se registran valores de bind. Se hicieron 10 ejecuciones
aisladas de la clase (20 escenarios, todos pasan):

| Ejecución | Cuenta ocupada (ms) | Token ocupado (ms) | SQLState original |
|---|---:|---:|---|
| 1 | 2141 | 2147 | 55P03 / 55P03 |
| 2 | 2150 | 2145 | 55P03 / 55P03 |
| 3 | 2147 | 2148 | 55P03 / 55P03 |
| 4 | 2141 | 2136 | 55P03 / 55P03 |
| 5 | 2139 | 2137 | 55P03 / 55P03 |
| 6 | 2129 | 2096 | 55P03 / 55P03 |
| 7 | 2136 | 2130 | 55P03 / 55P03 |
| 8 | 2141 | 2136 | 55P03 / 55P03 |
| 9 | 2153 | 2141 | 55P03 / 55P03 |
| 10 | 2164 | 2159 | 55P03 / 55P03 |

El mensaje original es `ERROR: canceling statement due to lock timeout`, con
`while locking tuple ... in relation "user_account"` o `"refresh_token"`.
En la suite completa instrumentada también pasan ambos (2137/2136 ms, 55P03).
Los logs históricos de los fallos de ~1 s ya contenían 55P03, no 40P01.

En la conexión del titular, `SHOW lock_timeout` después de su SELECT FOR UPDATE
devuelve `0` (no necesita esperar). En la transacción contendiente, tras el SET
de IdentityTransactions y los de Hibernate, antes y después de las lecturas que
logran bloquear, devuelve `2s`. Hibernate emite:

```sql
select current_setting('lock_timeout', true);
set local lock_timeout = 2000;
select ... from user_account uaje1_0 where uaje1_0.id=? for no key update of uaje1_0;
select ... from refresh_token rtje1_0 where rtje1_0.token_hash=? for no key update of rtje1_0;
set local lock_timeout = 2000;
```

No hay NOWAIT. Tras el SELECT fallido no puede medirse SHOW en esa transacción:
PostgreSQL devuelve 25P02 (transacción abortada); se registra conservando el 55P03
original. El valor inmediatamente anterior era 2s.

Conclusión: las esperas reproducidas son lock_timeout, no deadlock; Hibernate
no reduce aquí los 2 s. La causa de los ~1078 ms no queda demostrada: el observador
añade latencia y aislamiento de contexto, de modo que no prueba haber eliminado
una interferencia de suite. Hipótesis pendiente: interacción del contexto/ejecución
de suite o medición entre host Windows y PostgreSQL en Docker. No hay evidencia
para bajar umbrales, quitar hints o cambiar el timeout. Propuesta para confirmar
antes de corregir: medir reloj del servidor y del cliente, identificar backend y
separar titular/contendiente en hilos y transacciones independientes con barrera.
La corrección concreta debe esperar a reproducir y atribuir la discrepancia; no
se aplica ningún cambio de producción ni de umbrales en esta entrega.

Logs en `C:/Users/oscar/AppData/Local/Temp/opencode/`: `refresh-lock-diagnostic-01.log`,
`refresh-lock-diagnostic-2.log` a `refresh-lock-diagnostic-10.log`,
`refresh-lock-diagnostic-summary.txt`, `rbac-section2-guardians-before.log`,
`rbac-section2-guardians-after-and-targeted.log` y `rbac-section2-completed-verify.log`.

### Adaptación posterior de fixtures directas

La persona adaptó `StockReservationTransactionIT`,
`CatalogStockProjectionCacheInvalidationIT` y `ProductCacheMultiInstanceIT` mediante
el helper de test `AuthorizedCalls.withAuthorities`. Los dos IT no protegidos
`CatalogStockProjectionSchedulingIT` e `InventoryReservationComparisonIT` usan el
mismo helper con `product:write` y `stock:reserve`, respectivamente. En la prueba
concurrente, el contexto se establece dentro del hilo que invoca el caso de uso.
Los beans consumidos siguen siendo los decoradores de producción.

Tras estas adaptaciones, `bash ./mvnw -q verify` termina en verde: 479 tests
(282 Surefire y 197 Failsafe), sin fallos ni errores. Los informes contabilizados
son solo los de esta ejecución. `RefreshTokenLockTimeoutIT`, restaurado sin
instrumentación, pasa en esta ejecución; su intermitencia sigue abierta para el
final del change. Las tareas 2.3 y 2.4 permanecen completadas. Evidencia íntegra:
`C:/Users/oscar/AppData/Local/Temp/opencode/rbac-section2-fixtures-verify.log`.

## Avance de sección 3: identidad y objetos de pedidos

Base previa a esta tanda: `bash ./mvnw -q verify`, 479 tests sin fallos.
`CurrentOrderActor` es una API sin frameworks de ordering implementada desde
identity.infrastructure mediante una proyección por PK (solo id/customerId).
No introduce dependencia ordering → identity ni cache de identidad. El resultado
distingue cuenta ausente, cuenta sin cliente y cuenta vinculada. `OrderReadScope`
distingue `Own(customerId)`, `Global.INSTANCE` y `None.INSTANCE`, sin interpretar
un customerId nulo como acceso global. `ModuleDependenciesTest` e
`IdentityArchitectureTest` comprueban las fronteras de módulos.

Creación, cancelación y detalle usan decoradores proxificables: primero permiso,
después resolución de cuenta/alcance, después transacción y servicio puro. El cliente
recibido por la firma interna de creación/cancelación se ignora en la fachada
autorizada; la cuenta determina siempre el propietario. `RequestedOrderLine` lleva
la entrada sin validar al servicio para que construir `OrderLine` (y rechazar una
cantidad inválida) ocurra después de comprobar permiso y cliente asociado.

Los contratos HTTP de creación/cancelación/detalle ya no requieren X-Customer-Id,
no aceptan un selector de cliente y no exponen customerId. Los nombres históricos
de los dos escenarios de precedencia se conservan con pasos basados en permisos.
`OrderWebMapper`, `OrderPersistenceMapper` y `OrderSummaryPersistenceMapper`
concentran las conversiones, con pruebas unitarias. El detalle filtra id/alcance
mediante Specification tipada y carga una sola colección con EntityGraph. La
cancelación conserva tanto la lectura como el UPDATE condicional por id/cliente;
el permiso de lectura global no amplía las escrituras.

Ejemplos de peticiones (los identificadores de cliente no forman parte del cuerpo):

```http
POST /api/orders
Authorization: Bearer <token-customer>
Content-Type: application/json

{"lines":[{"productId":"11111111-1111-1111-1111-111111111111","quantity":3}]}

GET /api/orders/<id>
Authorization: Bearer <token-con-permiso-de-lectura>

POST /api/orders/<id>/cancellation
Authorization: Bearer <token-customer-propietario>
```

Por confirmación explícita de la persona durante apply, las partes de listado de
3.2 y 3.7 quedan pendientes junto a 3.4/3.8 y esas dos tareas no se marcan todavía.
El listado mantiene su comportamiento previo hasta que se adapte el guardián 1.4.
No se han cambiado la firma de findByStatus ni el test guardián.

### Ajuste humano de trazabilidad de specs

La persona corrigió `scripts/spec-trace.sh`: para cada capability, el script omite
los escenarios pertenecientes a requirements declarados bajo `## REMOVED Requirements`
por un change activo. Los changes archivados no aportan estas exclusiones. Así deja
de exigir pruebas del contrato retirado de X-Customer-Id mientras las specs principales
esperan su sincronización humana. Los demás escenarios siguen exigiendo su
`@DisplayName` exacto; no se han restaurado assertions 400 del contrato eliminado
ni se han modificado las specs principales. El agente no editó el script protegido.
`bash ./verify-quality.sh --fast` pasa tras este ajuste humano.

### Evidencia al detenerse antes del listado

La comprobación fast tras la corrección humana pasa: specs activas 9/9, archivadas
12/12, 7 escenarios omitidos por requirements REMOVED y todos los demás trazados,
Spotless, Checkstyle y 297 tests fast sin fallos, errores ni omitidos.
`bash ./mvnw -q verify` pasa con 297 tests Surefire y 205 Failsafe (502 en total),
sin fallos, errores ni omitidos. `RefreshTokenLockTimeoutIT` pasa en esta ejecución;
el diagnóstico intermitente sigue abierto, sin instrumentación ni cambios de umbral.

La base previa tenía 479 tests y lista de fallos vacía. La lista final también está
vacía: diff de fallos vacío. Se completan 3.1, 3.3, 3.5 y 3.6 (11/27 tareas totales).
3.2 y 3.7 conservan pendientes sus partes de listado por el acuerdo explícito con
la persona; 3.4 y 3.8 siguen bloqueadas por 1.4. El guardián de 120 pedidos y la firma
actual del puerto de listado conservan diff vacío.

Evidencia íntegra en `C:/Users/oscar/AppData/Local/Temp/opencode/`:
`rbac-section3-baseline.log`, `rbac-section3-baseline-failures.txt`,
`rbac-section3-spec-trace-fast.log`, `rbac-section3-final-verify.log`,
`rbac-section3-final-failures.txt` y `rbac-section3-failures.diff`.

### Propuesta exacta pendiente de 1.4 (no implementada)

Nuevo record en `com.codeja.master_acdc.ordering.domain.model`:

```java
public record OrderListingCriteria(OrderStatus status, OrderReadScope scope) {
  public OrderListingCriteria {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(scope, "scope");
  }
}
```

En el puerto `ordering.domain.port.OrderSummaryQuery` y el adaptador
`ordering.infrastructure.adapters.out.persistence.OrderSummaryQueryAdapter`:

```java
List<OrderSummary> findByStatus(OrderListingCriteria criteria);
```

Se sustituye la firma anterior; no se añade sobrecarga de compatibilidad. En el
guardián `OrderSummaryQueryAdapterQueryCountTest`, solo cambia la invocación y
se añaden los imports del criterio y alcance:

```java
var summaries = adapter.findByStatus(
    new OrderListingCriteria(OrderStatus.PAID, OrderReadScope.Global.INSTANCE));
```

Permanecen ORDER_COUNT=120, fixtures, flush/clear/Statistics.clear, tamaño de
resultado y umbral ≤2 sentencias. La futura proyección filtrará estado/alcance
en BD; el guardián mide solo el adaptador, mientras 3.8 medirá también el lookup
de identidad en HTTP. Crear el record y cambiar la firma serán parte de 3.4;
no se espera que un guardián adaptado a tipos aún inexistentes compile hasta ese paso.

## 6. Revocación inmediata para cuentas bloqueadas

## 7. Matriz de tests

## 8. Protege el loop

## 9. Demuéstralo con agentes
