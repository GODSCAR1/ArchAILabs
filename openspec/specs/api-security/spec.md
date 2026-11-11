# api-security Specification

## Purpose
Define la frontera de autenticación HTTP de la API y su aislamiento respecto de operaciones, manteniendo públicas las lecturas del catálogo y protegiendo las operaciones basadas en cookies.

## Requirements

### Requirement: API stateless con excepciones públicas explícitas
El sistema SHALL permitir sin autenticación GET `/api/products/**`, GET `/api/categories/**` y POST `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/password-reset/request`, `/api/auth/password-reset/confirm`. El resto de `/api/**` SHALL requerir bearer JWT válido. SHALL no crear ni usar sesión HTTP para autenticar la API. Este change define autenticación; la autorización se define en add-rbac-and-object-authorization.

#### Scenario: Lectura pública del catálogo
- **WHEN** se consulta anónimamente un producto existente con GET `/api/products/{sku}`
- **THEN** responde HTTP 200 sin cookie de sesión de autenticación

#### Scenario: Reserva bajo products no es pública
- **WHEN** se envía sin bearer POST `/api/products/{id}/reservations` con datos válidos
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin modificar pedidos ni stock

#### Scenario: Sesión HTTP no sustituye bearer
- **WHEN** se llama GET `/api/orders?status=PAID` sin bearer usando solo una cookie de sesión previa
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`; pedidos y stock permanecen sin cambios

#### Scenario: Recuperación pública sin sesión ni cookie
- **GIVEN** una cuenta habilitada y una contraseña nueva válida
- **WHEN** solicita recuperación y la confirma con el token recibido por el canal de notificación, sin bearer ni cookies
- **THEN** POST `/api/auth/password-reset/request` responde HTTP 202 y POST `/api/auth/password-reset/confirm` responde HTTP 204
- **AND** ninguna respuesta emite sesión HTTP, access token ni cookie de autenticación; pedidos y stock no cambian

### Requirement: CSRF limitado a refresh y logout
El sistema SHALL exigir en POST refresh/logout la cookie XSRF-TOKEN y su valor crudo en X-XSRF-TOKEN, además de refresh_token. SHALL emitir XSRF-TOKEN efectivamente en login sin exigir CSRF en login, registro o llamadas bearer. El rechazo CSRF SHALL preceder al procesamiento del refresh y no consumirlo ni revocarlo.

#### Scenario: Refresh sin cabecera CSRF
- **WHEN** se envía POST `/api/auth/refresh` con cookies válidas pero sin X-XSRF-TOKEN
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`, sin rotar ni revocar la familia y sin modificar pedidos ni stock

#### Scenario: Logout con CSRF incorrecto
- **WHEN** se envía POST `/api/auth/logout` con refresh válido y X-XSRF-TOKEN distinto de la cookie o sin cookie CSRF
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`, sin revocar la familia ni modificar pedidos o stock

#### Scenario: Cabecera CSRF acepta valor crudo
- **WHEN** la SPA, desde una ruta fuera de /api/auth, lee mediante document.cookie el XSRF-TOKEN con Path=/ emitido en login, lo copia literalmente a X-XSRF-TOKEN y solicita refresh con ambas cookies
- **THEN** responde HTTP 200 con un nuevo par de tokens

#### Scenario: Registro y login no requieren CSRF
- **WHEN** se registra una cuenta válida y después inicia sesión sin cabecera CSRF
- **THEN** registro responde HTTP 201 y login HTTP 200 con cookie XSRF-TOKEN materializada

### Requirement: Errores de seguridad interoperables
El sistema SHALL devolver los errores 401/403 como application/problem+json con type, title, status, detail e instance. SHALL conservar WWW-Authenticate donde el protocolo requiera challenge, incluido Bearer en API y Basic en operaciones, y no revelar secretos ni datos personales en errores o logs.

#### Scenario: Error bearer conserva challenge y formato
- **WHEN** se llama GET `/api/orders?status=PAID` con bearer inválido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, los cinco campos, instance de la petición, Content-Type application/problem+json y WWW-Authenticate Bearer
- **AND** no modifica pedidos ni stock

#### Scenario: Error CSRF conserva formato de problema
- **WHEN** se solicita logout sin protección CSRF válida
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden` y los cinco campos en application/problem+json, sin modificar familias, pedidos ni stock

### Requirement: Operaciones aisladas de los bearer de API
El sistema SHALL permitir health de Actuator públicamente y exigir Basic con ROLE_OPS en los demás endpoints de Actuator, usando SHOP_OPS_USER y SHOP_OPS_PASSWORD_HASH externos. SHALL no autenticar esas peticiones mediante bearer de API ni aceptar las credenciales Basic de operaciones para la API.

#### Scenario: Health público
- **WHEN** se consulta GET `/actuator/health` sin credenciales y las dependencias están sanas
- **THEN** responde HTTP 200

#### Scenario: Bearer ADMIN no autentica operaciones
- **WHEN** se consulta GET `/actuator/metrics` con un JWT ADMIN válido de la API y sin Basic
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y WWW-Authenticate Basic; pedidos y stock permanecen sin cambios

#### Scenario: Operador accede con Basic
- **WHEN** se consulta GET `/actuator/metrics` con las credenciales Basic válidas de OPS
- **THEN** responde HTTP 200

#### Scenario: Basic de operaciones no autentica API
- **WHEN** se consulta GET `/api/orders?status=PAID` con Basic de OPS pero sin bearer
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer, sin modificar pedidos ni stock
