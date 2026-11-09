# Incluir el email del usuario en el JWT

**Session ID:** ses_e3e98f3ffffe2dzZBK50qUXHHY
**Created:** 9/11/2026, 12:41:58
**Updated:** 9/11/2026, 12:42:08

---

## User

mete el email del usuario en el JWT para ahorrar una consulta

---

## Assistant (Build · GPT-6.1 Sol · 3.3s)

**Tool: read**

**Input:**
```json
{
  "filePath": "C:\\Users\\oscar\\Desktop\\Master Arquitecto IA\\master-acdc\\docs\\adr\\0011-autenticacion-refresh-y-version-de-sesion.md"
}
```

**Output:**
```
<path>C:\Users\oscar\Desktop\Master Arquitecto IA\master-acdc\docs\adr\0011-autenticacion-refresh-y-version-de-sesion.md</path>
<type>file</type>
<content>
1: # ADR 0011 — Autenticación stateless, refresh tokens rotatorios y versión de sesión
2: 
3: - Estado: aceptado (lab S12, 2026-11-09)
4: - Changes: `add-authentication`, `add-rbac-and-object-authorization`, `add-session-version-revocation`
5: 
6: ## Contexto
7: 
8: La API no tenía autenticación y el cliente de un pedido se elegía con la cabecera `X-Customer-Id`
9: (cualquiera podía actuar como cualquier cliente: BOLA). El negocio exige catálogo público, pedidos
10: propios, roles de catálogo/soporte/administración y que cambiar la contraseña o bloquear una cuenta
11: cierre todas las sesiones al instante.
12: 
13: ## Decisión
14: 
15: 1. **Access token JWT RS256 de 15 min**, firmado con una clave RSA 2048 externa (`SHOP_JWT_PRIVATE_KEY`,
16:    nunca en el repo) y `kid` = thumbprint RFC 7638. Encoder y decoder salen del mismo `JWKSource`; la
17:    clave pública se publica en `/.well-known/jwks.json`. Claims **exactos**: `iss, sub, aud, iat, exp,
18:    jti, roles, sv`. Ni email, ni permisos, ni customerId en el token.
19: 2. **Refresh token opaco rotatorio** (32 bytes, solo su SHA-256 en BD) en cookie `HttpOnly; Secure;
20:    SameSite=Strict; Path=/api/auth`. Vence a los 7 días o a los 30 días del login (lo primero). Cada uso
21:    lo rota; **reutilizar uno ya rotado revoca la familia entera** y esa revocación se confirma (commit)
22:    antes de responder 401. Orden de locks único: cuenta → token (`FOR UPDATE`), `lock_timeout` 2 s → 503.
23: 3. **CSRF solo donde hay cookie**: `/api/auth/refresh` y `/api/auth/logout`, con `XSRF-TOKEN` legible
24:    por JS (`Path=/`) y cabecera `X-XSRF-TOKEN` con el valor crudo. Las llamadas con bearer no usan CSRF.
25: 4. **RBAC por permisos**: el token lleva roles; el conversor deriva `ROLE_<rol>` y los permisos del enum
26:    `Role`. Los endpoints comprueban permisos (`hasAuthority`), no roles, salvo `/api/admin/**`.
27: 5. **Autorización a nivel de objeto en la consulta**: el cliente sale del `sub` vía la cuenta; pedidos
28:    ajenos → 404. `order:read-any` amplía solo lecturas, nunca escrituras.
29: 6. **Versión de sesión (`sv`)**: `user_account.session_version` es la verdad; Redis
30:    (`shop:identity:session-version:v1:<uuid>`, sin TTL, `noeviction`) es la barrera que se consulta en
31:    cada petición. Cambiar contraseña o bloquear incrementa la versión, revoca familias y **publica la
32:    barrera en Redis antes del commit** con una escritura monótona; Redis caído = **401 (fail closed)**.
33: 
34: ## Consecuencias
35: 
36: - Revocación inmediata con coste medido: ~0,7 ms p50 por petición con la clave en Redis; ~200 ms y 401
37:   si Redis no responde (timeout 200 ms). Ver `docs/lab-s12.md`.
38: - Un fallo a medias deja el sistema del lado seguro: como mucho cierra sesiones de más, nunca acepta un
39:   token viejo. Redis de versiones es estado derivado: tras perderlo se reconstruye desde PostgreSQL.
40: - La versión de sesión es la **única excepción** a "Redis caído = miss" (AGENTS.md, `docs/caching.md`).
41: - Deuda registrada: el contrato `inventory.api` usa tipos de dominio y Spring (regla ArchUnit congelada).
42: 
43: ## Alternativas descartadas
44: 
45: - Sesión HTTP con estado: no escala horizontalmente y mezcla CSRF en toda la API.
46: - Access token largo sin refresh: revocar exige lista negra completa.
47: - customerId o permisos en el JWT: datos obsoletos durante 15 min y claims que crecen.
48: - Commit en BD y luego actualizar Redis: deja una ventana en la que el token viejo sigue válido.

(End of file - total 48 lines)
</content>
```

---

## User

[AC/DC contexto de sesión]
Rama: lab/s12
Umbral PIT vigente: 75%
Hallazgos abiertos (no introduzcas más de estos tipos):
ninguno

---

## Assistant (Build · GPT-6.1 Sol · 7.0s)

Añadir el email al JWT **contradice el contrato de seguridad vigente**: el [ADR 0011](docs/adr/0011-autenticacion-refresh-y-version-de-sesion.md) fija los claims exactos `iss, sub, aud, iat, exp, jti, roles, sv` y excluye expresamente el email. Además, `JwtTokenAdapter` y las pruebas de claims son zonas protegidas.

Para hacerlo necesito tu autorización explícita para revisar ese contrato y los archivos protegidos afectados. El cambio empezaría con una propuesta OpenSpec para revisión humana antes de implementarlo.

No he modificado código ni ejecutado Verify al detenerme en esta restricción. El email en un JWT firmado es legible y puede quedar obsoleto durante sus 15 minutos de validez.

---

