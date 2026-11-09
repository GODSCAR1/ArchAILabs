# Proposal

## Why

Los access tokens actuales siguen siendo utilizables hasta 900 segundos después de cambiar la contraseña; tampoco existe una operación administrativa de bloqueo. El paso 6 de S12 exige cerrar inmediatamente ese acceso y todas las familias de renovación, conservando los contratos de autenticación y autorización ya archivados.

## What Changes

- **BREAKING**: emitir exactamente ocho claims (`iss`, `sub`, `aud`, `iat`, `exp`, `jti`, `roles`, `sv`) en login y refresh; rechazar los tokens anteriores sin `sv`. Los deltas modifican explícitamente ambos escenarios vigentes de claims exactos, de siete a ocho.
- Validar la versión de sesión en cada autenticación bearer mediante `SessionVersionValidator`, añadido al decoder junto a issuer, audience y tiempo. PostgreSQL conserva la verdad; Redis mantiene una clave por cuenta, sin L1 ni aceptación ante errores.
- Redis indisponible durante validación implica HTTP 401 y WARN sin token ni datos personales; clave ausente implica lectura coordinada de PostgreSQL y población de Redis.
- Cambiar contraseña o bloquear incrementa la versión en la misma transacción que revoca todas las familias. Publicar primero una barrera de versión en Redis evita el intervalo inseguro BD-confirmada/cache-antigua; el diseño incluye carreras y fallos parciales.
- Añadir POST `/api/admin/users/{id}/block` y `/unblock`, con ADMIN y `user:manage`; desbloquear no recupera tokens ni familias revocados.
- Extender `SecurityMatrixTest` y `AuthFlowIT`; medir una lectura Redis por autenticación con clave presente, ausencia de consultas adicionales de versión a BD y latencia añadida.

## Capabilities

### New Capabilities

Ninguna: la revocación pertenece al ciclo de autenticación y la administración existentes.

### Modified Capabilities

- `authentication`: ocho claims, comprobación de versión, cierre inmediato por contraseña y fallo seguro del almacén de versiones.
- `authorization`: permiso ADMIN `user:manage`, ocho claims y dos filas nuevas en la matriz existente.
- `user-administration`: bloqueo y desbloqueo con doble autorización, efectos de sesión y errores.

## Impact

- `identity` (emisor, decoder, puertos de versión, servicios, adaptadores Redis y ensamblaje transaccional); PostgreSQL ya tiene `session_version`, por lo que no se prevé migración ni dependencia nueva. Redis reutiliza el timeout existente de 200 ms, también con cache de catálogo desactivada.
- Documentación posterior: fila de cache ANTES del código en `docs/caching.md`, tabla rol → permisos y evidencia/latencia en `docs/lab-s12.md`. Inventario concreto de fixtures y contextos en `design.md`.
- Tokens firmados fuera del emisor: `AuthHttpSupport.signedToken` y la re-firma de `JwtTokenAdapterTest`; objetos JWT de `RolesAndPermissionsConverterTest` y `jwt()` de slices no prueban el decoder. Fixtures que sí usan el emisor pero no persisten cuenta (`CatalogManagerToken`, guardián de reservas) también resultan incompatibles. No se añadirá un bypass de producción para conservarlas.
- **Intervención humana previa**: adaptar el guardián `StockReservationConcurrencyTest` (cuenta persistida + Redis real, conservando 50 reservas, 30/20 y timeout); revisar cualquier otro impacto de `scripts/protected-tests.txt`. El agente no editará guardianes ni firmas para sortearlos.
- **Conflictos que requieren decisión humana antes de implementar**: AGENTS.md y `docs/caching.md` generalizan Redis caído = miss, valores `ProductDetail`, TTL con jitter centralizados y actualización después del commit. Este control de seguridad requiere fail-closed, valor de versión inmutable, sin L1/TTL y barrera antes del commit. Se propone una excepción explícita y acotada para versiones de identidad; no modificar `CacheTtlPolicy`, `ProductDetailCache` ni la política de catálogo. La aprobación de la propuesta por sí sola no autoriza editar archivos protegidos.
- El contexto de `openspec/config.yaml` aún dice que no hay autenticación y se usa X-Customer-Id: contradice las specs archivadas y la implementación. Esta propuesta se fundamenta en estas últimas; la corrección del contexto protegido queda a la persona.
- La protección del paso 8 (configuración de seguridad, Role, servicio de tokens, matriz, hooks y plugin) corresponde a la persona después. No se modifica `scripts/`, hooks, plugin, configuración protegida ni specs principales durante esta planificación.

## Non-goals

- Implementar código en este workflow, sincronizar o archivar specs, completar el paso 8 o redactar evidencia de pruebas aún no ejecutadas.
- Cambiar logout a revocación de access tokens, reglas CSRF, firma/duración, semántica de roles existentes, checkout, cache de productos o deuda congelada de inventario.
- Aceptar tokens legacy sin `sv`, añadir fallback permisivo, dependencias, jobs de reconciliación o una transacción distribuida.
- Alterar el umbral PIT del 75 % o ampliar el baseline de hallazgos declarado vacío.
