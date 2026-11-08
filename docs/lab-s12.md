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

## 6. Revocación inmediata para cuentas bloqueadas

## 7. Matriz de tests

## 8. Protege el loop

## 9. Demuéstralo con agentes
