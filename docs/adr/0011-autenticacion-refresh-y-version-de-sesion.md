# ADR 0011 — Autenticación stateless, refresh tokens rotatorios y versión de sesión

- Estado: aceptado (lab S12, 2026-11-09)
- Changes: `add-authentication`, `add-rbac-and-object-authorization`, `add-session-version-revocation`

## Contexto

La API no tenía autenticación y el cliente de un pedido se elegía con la cabecera `X-Customer-Id`
(cualquiera podía actuar como cualquier cliente: BOLA). El negocio exige catálogo público, pedidos
propios, roles de catálogo/soporte/administración y que cambiar la contraseña o bloquear una cuenta
cierre todas las sesiones al instante.

## Decisión

1. **Access token JWT RS256 de 15 min**, firmado con una clave RSA 2048 externa (`SHOP_JWT_PRIVATE_KEY`,
   nunca en el repo) y `kid` = thumbprint RFC 7638. Encoder y decoder salen del mismo `JWKSource`; la
   clave pública se publica en `/.well-known/jwks.json`. Claims **exactos**: `iss, sub, aud, iat, exp,
   jti, roles, sv`. Ni email, ni permisos, ni customerId en el token.
2. **Refresh token opaco rotatorio** (32 bytes, solo su SHA-256 en BD) en cookie `HttpOnly; Secure;
   SameSite=Strict; Path=/api/auth`. Vence a los 7 días o a los 30 días del login (lo primero). Cada uso
   lo rota; **reutilizar uno ya rotado revoca la familia entera** y esa revocación se confirma (commit)
   antes de responder 401. Orden de locks único: cuenta → token (`FOR UPDATE`), `lock_timeout` 2 s → 503.
3. **CSRF solo donde hay cookie**: `/api/auth/refresh` y `/api/auth/logout`, con `XSRF-TOKEN` legible
   por JS (`Path=/`) y cabecera `X-XSRF-TOKEN` con el valor crudo. Las llamadas con bearer no usan CSRF.
4. **RBAC por permisos**: el token lleva roles; el conversor deriva `ROLE_<rol>` y los permisos del enum
   `Role`. Los endpoints comprueban permisos (`hasAuthority`), no roles, salvo `/api/admin/**`.
5. **Autorización a nivel de objeto en la consulta**: el cliente sale del `sub` vía la cuenta; pedidos
   ajenos → 404. `order:read-any` amplía solo lecturas, nunca escrituras.
6. **Versión de sesión (`sv`)**: `user_account.session_version` es la verdad; Redis
   (`shop:identity:session-version:v1:<uuid>`, sin TTL, `noeviction`) es la barrera que se consulta en
   cada petición. Cambiar contraseña o bloquear incrementa la versión, revoca familias y **publica la
   barrera en Redis antes del commit** con una escritura monótona; Redis caído = **401 (fail closed)**.

## Consecuencias

- Revocación inmediata con coste medido: ~0,7 ms p50 por petición con la clave en Redis; ~200 ms y 401
  si Redis no responde (timeout 200 ms). Ver `docs/lab-s12.md`.
- Un fallo a medias deja el sistema del lado seguro: como mucho cierra sesiones de más, nunca acepta un
  token viejo. Redis de versiones es estado derivado: tras perderlo se reconstruye desde PostgreSQL.
- La versión de sesión es la **única excepción** a "Redis caído = miss" (AGENTS.md, `docs/caching.md`).
- Deuda registrada: el contrato `inventory.api` usa tipos de dominio y Spring (regla ArchUnit congelada).

## Alternativas descartadas

- Sesión HTTP con estado: no escala horizontalmente y mezcla CSRF en toda la API.
- Access token largo sin refresh: revocar exige lista negra completa.
- customerId o permisos en el JWT: datos obsoletos durante 15 min y claims que crecen.
- Commit en BD y luego actualizar Redis: deja una ventana en la que el token viejo sigue válido.
