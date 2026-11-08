# Proposal

## Why

La API permite actualmente operaciones de negocio sin autenticar al llamante. El lab S12 necesita una base de identidad verificable y stateless antes de introducir permisos, propiedad de recursos y revocación inmediata de access tokens.

## What Changes

- Nuevo módulo hexagonal `identity`: registro atómico de cuenta y cliente, login, refresh rotatorio con detección de reutilización, logout y cambio de contraseña.
- V8 en PostgreSQL: cuentas con email único normalizado, contraseña delegada `{bcrypt}`, roles, enabled, `session_version=0` reservado y FK nullable única a customers; refresh almacenado únicamente como SHA-256, con índices y vencimientos individual y de familia.
- JWT RS256 de 15 minutos con exactamente `iss`, `sub`, `aud`, `iat`, `exp`, `jti`, `roles`; clave RSA 2048 externa, `kid` RFC 7638 y publicación pública de JWKS. Emisor y receptor comparten JWKSource y valores exactos de issuer/audience.
- Cookies seguras: refresh_token HttpOnly con Path=/api/auth y XSRF-TOKEN legible por JS con Path=/, ambas Secure y SameSite=Strict. CSRF únicamente en refresh/logout, emisión efectiva de XSRF-TOKEN en login y errores 401/403 ProblemDetail conservando los challenges de autenticación.
- **BREAKING**: únicamente GET de productos/categorías y los cuatro POST de autenticación son públicos dentro de `/api/**`; el resto exige JWT, incluidas escrituras de catálogo, reservas y pedidos. Se conservan los contratos de negocio después de autenticar y `X-Customer-Id` en pedidos.
- Actuator aislado: health público y resto exclusivamente Basic con ROLE_OPS y credenciales externas; un bearer ADMIN de la API no autentica allí.
- Registro `{email,password,name?}` → 201 solo `{id}`, sin cabecera Location; nombre ausente derivado de la parte local del email. Login separado. Refresh con 7 días deslizantes por token y máximo absoluto de familia de 30 días.

## Non-goals

- RBAC/permisos de API, `@PreAuthorize`, propiedad de pedidos y eliminación de `X-Customer-Id`: change posterior `add-rbac-and-object-authorization`.
- Claim `sv`, consulta de versiones en Redis y revocación inmediata del JWT: change posterior `add-session-version-revocation`. Logout/cambio de contraseña revocan refresh; los access tokens ya emitidos mantienen su vigencia temporal.
- Implementar el resto de pasos 5–9 del lab, crear GET /api/users/me, gestión de usuarios o rotación operativa multiclave.
- Alterar caches, reglas de negocio, umbral PIT (75%) o guardianes para facilitar el cambio.

## Capabilities

### New Capabilities

- `authentication`: ciclo de cuentas, credenciales, tokens y familias; publicación de claves y contrato de errores.
- `api-security`: frontera stateless, rutas públicas, CSRF y aislamiento de Actuator.

### Modified Capabilities

- `ordering`: añade autenticación como precondición a los contratos actuales, sin cambiar la identificación temporal por cabecera.
- `inventory`: añade autenticación a la reserva HTTP directa; no a la fachada interna.
- `product-editing`: añade autenticación a creación/edición conservando lecturas públicas y contratos posteriores a la autenticación.

## Impact

- `src/main/java/com/codeja/master_acdc/identity/**`, migración V8, recurso de contraseñas comunes, `.gitignore` y nuevos tests unitarios, ArchUnit e integración. Integración mínima con la creación de customers mediante API pública de ordering, sin importar su persistencia desde identity.
- Adaptación de tests HTTP de catálogo, pedidos e inventario; inventario detallado y estrategia en `design.md`. AuthFlowIT usa tokens reales y Testcontainers; las pruebas de negocio MockMvc usan `jwt()`.
- Una extensión JUnit global autodetectada genera una RSA 2048 por JVM y un PEM temporal fuera del repo, y fija propiedades de sistema para ejercitar el mismo cargador de PEM que producción, incluidos contextos SpringApplicationBuilder. Propiedad canónica: `shop.jwt.private-key`, alimentada en producción desde `SHOP_JWT_PRIVATE_KEY`; issuer `https://api.master-acdc.dev` y audience `master-acdc-api`.
- **Intervención humana obligatoria en tests**: CatalogProductEditingMongoIT y CatalogProductCacheEditingIT (jwt()), y StockReservationConcurrencyTest (bearer real). Los demás contextos reciben las propiedades globales sin imports de configuración efímera; si otro guardián falla, se detiene la aplicación para revisión humana.
- `pom.xml` ya tiene cambios locales del usuario con starters de Security, Resource Server y tests. Es zona protegida: revisar su compatibilidad y cualquier dependencia adicional con una persona; no sobrescribirlos. También requieren permiso los cambios a `src/main/resources/application*`, scripts, configuración del loop o zonas protegidas si fueran necesarios.
- El contexto OpenSpec todavía describe una API sin autenticación y solo catálogo: el código ya tiene ordering/inventory y catálogo Mongo. Este delta declara la nueva precondición sin editar ese archivo protegido ni las specs principales.
- Los ADR 0007, 0009 y 0010 se respetan: sin cambios de locks de stock, almacenamiento del catálogo ni cache. Las discrepancias con el enunciado completo (sv, permisos, eliminación de customerId) son recortes explícitos por la separación en tres changes acordada por Oscar.

Esta propuesta autoriza únicamente planificación. La aplicación espera revisión humana y una petición nueva de apply.
