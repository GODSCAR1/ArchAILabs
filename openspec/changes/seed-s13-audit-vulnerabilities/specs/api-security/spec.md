# Spec Delta

## MODIFIED Requirements

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
