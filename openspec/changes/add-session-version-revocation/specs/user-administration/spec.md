# Spec Delta

## ADDED Requirements

### Requirement: Bloqueo administrativo revoca todas las sesiones
POST `/api/admin/users/{id}/block` SHALL exigir ADMIN y `user:manage` antes de consultar la cuenta objetivo. SHALL deshabilitar la cuenta, incrementar su versión de sesión y revocar todas sus familias atómicamente. SHALL responder 204 sin cuerpo solo con la barrera de revocación establecida. Repetir block SHALL volver a incrementar la versión y revocar cualquier familia restante.

#### Scenario: Administrador bloquea y cierra acceso inmediatamente
- **WHEN** ADMIN con user:manage bloquea una cuenta existente con dos dispositivos autenticados
- **THEN** POST `/api/admin/users/{id}/block` responde HTTP 204 sin cuerpo, deja la cuenta deshabilitada, incrementa su versión y revoca todas sus familias
- **AND** los access tokens anteriores en `GET /api/orders?status=PAID` responden HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`; login responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-credentials` y refresh con CSRF correcto HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token`
- **AND** no emite tokens nuevos ni cambia otras cuentas, sus familias, pedidos o stock

#### Scenario: Bloqueo repetido no recupera sesiones
- **WHEN** ADMIN con user:manage repite POST `/api/admin/users/{id}/block` de una cuenta ya bloqueada
- **THEN** responde HTTP 204 sin cuerpo, conserva enabled=false, incrementa la versión otra vez y conserva todas las familias revocadas; pedidos y stock no cambian

### Requirement: Desbloqueo no restaura sesiones anteriores
POST `/api/admin/users/{id}/unblock` SHALL exigir ADMIN y `user:manage` antes de consultar la cuenta objetivo. SHALL habilitar la cuenta sin reducir ni incrementar su versión ni reactivar familias. SHALL responder 204 sin cuerpo incluso si ya estaba habilitada. El usuario SHALL iniciar una sesión nueva para obtener credenciales utilizables.

#### Scenario: Desbloqueo permite login nuevo pero no tokens anteriores
- **WHEN** ADMIN con user:manage desbloquea una cuenta previamente bloqueada mediante POST `/api/admin/users/{id}/unblock`
- **THEN** responde HTTP 204 sin cuerpo y el login posterior con credenciales correctas responde HTTP 200 con la versión vigente
- **AND** el access token previo al bloqueo en `GET /api/orders?status=PAID` responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y su refresh HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token`; no modifica pedidos ni stock

#### Scenario: Desbloqueo repetido conserva versión y familias
- **WHEN** ADMIN con user:manage solicita POST `/api/admin/users/{id}/unblock` para una cuenta ya habilitada
- **THEN** responde HTTP 204 sin cuerpo, conserva versión y estado de familias, pedidos y stock

### Requirement: Rechazos administrativos conservan el estado
Bloquear o desbloquear sin bearer válido SHALL devolver 401; con bearer sin ADMIN o sin `user:manage`, 403 antes de consultar el objetivo. Una cuenta objetivo inexistente o un id inválido SHALL producir 404 tras autorizar. Contención de cuenta SHALL conservar el contrato 503 con Retry-After: 1. Ningún rechazo SHALL modificar pedidos ni stock.

#### Scenario: Gestión de cuenta exige doble autorización
- **WHEN** se solicita POST `/api/admin/users/{id}/block` o `/unblock` sin bearer, con CUSTOMER, CATALOG_MANAGER o SUPPORT, o faltando uno de ADMIN y user:manage
- **THEN** sin bearer válido responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer; autenticado sin ambos requisitos responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`
- **AND** no consulta el objetivo ni modifica cuentas, versiones, familias, pedidos o stock

#### Scenario: Cuenta objetivo inexistente o inválida
- **WHEN** ADMIN con user:manage solicita POST `/api/admin/users/{id}/block` o `/unblock` con un id inexistente o no válido
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/user-not-found`; cuentas, versiones, familias, pedidos y stock permanecen sin cambios

#### Scenario: Cuenta objetivo ocupada no cambia parcialmente
- **WHEN** ADMIN con user:manage solicita POST `/api/admin/users/{id}/block` o `/unblock` y no logra el bloqueo en dos segundos
- **THEN** responde HTTP 503 con type `https://api.master-acdc.dev/problems/authentication-busy` y Retry-After: 1; cuentas, versiones, familias, pedidos y stock permanecen sin cambios

#### Scenario: Fallo compartido durante bloqueo no confirma cambios
- **WHEN** POST `/api/admin/users/{id}/block` ya autorizado no logra publicar la barrera de versión, incluso con escritura de resultado incierto
- **THEN** responde HTTP 503 con type `https://api.master-acdc.dev/problems/session-version-unavailable` y Retry-After: 1; enabled, versión persistente, familias, pedidos y stock permanecen sin cambios
- **AND** no restaura una barrera que pudiera haberse escrito; durante la indisponibilidad las llamadas bearer responden HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`
