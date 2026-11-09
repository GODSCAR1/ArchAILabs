# Spec Delta

## Purpose

Permite a administración consultar cuentas de forma paginada sin exponer credenciales ni material de renovación de sesiones.

## ADDED Requirements

### Requirement: Consulta administrativa con doble autorización
GET `/api/admin/users` SHALL exigir rol ADMIN y permiso `user:read`. SHALL devolver HTTP 200 con `content`, `page`, `size`, `totalElements`, `totalPages`; cada elemento SHALL contener solo `id`, `email`, `roles`, `enabled`. SHALL no devolver password_hash, contraseñas, identificadores de cliente, versión de sesión ni datos de refresh.

#### Scenario: Administrador consulta cuentas sin secretos
- **WHEN** ADMIN solicita GET `/api/admin/users?page=0&size=20`
- **THEN** responde HTTP 200 con la página y exclusivamente los campos declarados; pedidos y stock no cambian

#### Scenario: Usuario sin rol administrativo no obtiene cuentas
- **WHEN** CUSTOMER, CATALOG_MANAGER o SUPPORT solicita GET `/api/admin/users`
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`, sin datos de cuentas ni cambios de pedidos o stock

#### Scenario: Consulta administrativa anónima
- **WHEN** se solicita GET `/api/admin/users` sin bearer válido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin datos de cuentas ni cambios de pedidos o stock

### Requirement: Paginación administrativa acotada y estable
El listado SHALL usar page=0 y size=20 por defecto, page ≥0, size entre 1 y 100 y orden ascendente fijo por id. SHALL rechazar parámetros de ordenación y paginación inválida con HTTP 400 y type `https://api.master-acdc.dev/problems/invalid-user-page`.

#### Scenario: Páginas administrativas estables
- **WHEN** ADMIN consulta páginas consecutivas de tamaño 20 sobre un conjunto estable de 120 cuentas
- **THEN** responde HTTP 200 sin repeticiones, totalElements=120, totalPages=6; una página posterior a la última contiene content vacío y conserva el total

#### Scenario: Paginación administrativa inválida
- **WHEN** ADMIN solicita GET `/api/admin/users` con page=-1, size=0, size=101, un valor no numérico o sort=password_hash
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-user-page`; pedidos, cuentas y stock no cambian
