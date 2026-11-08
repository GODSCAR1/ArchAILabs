# Spec Delta

## Purpose

Restringe las operaciones de la tienda por permisos derivados de roles, preservando las lecturas públicas y evitando que autenticar equivalga a autorizar.

## ADDED Requirements

### Requirement: Permisos explícitos por rol
El sistema SHALL conceder únicamente los permisos de esta tabla y unirlos para cuentas con varios roles. ADMIN SHALL no heredar permisos CUSTOMER.

| Rol | Permisos |
|---|---|
| CUSTOMER | order:create, order:read-own, order:cancel-own |
| CATALOG_MANAGER | product:write, stock:reserve |
| SUPPORT | order:read-any, order:refund |
| ADMIN | product:write, stock:reserve, order:read-any, order:refund, user:read |

#### Scenario: ADMIN no puede crear pedidos por su rol
- **WHEN** un bearer válido con solo ADMIN solicita POST `/api/orders` con líneas válidas
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; pedidos y stock no cambian

#### Scenario: Roles combinados suman permisos sin perder restricciones
- **WHEN** una cuenta CUSTOMER y CATALOG_MANAGER con cliente asociado crea un pedido válido con stock disponible
- **THEN** POST `/api/orders` responde HTTP 201; sus permisos incluyen creación de pedidos y reserva directa

### Requirement: Authorities derivadas exclusivamente de roles conocidos
El sistema SHALL derivar ROLE_<rol> y permisos solo de roles conocidos del bearer validado. Roles desconocidos SHALL no conceder ninguna authority. Los claims emitidos SHALL seguir siendo exactamente iss, sub, aud, iat, exp, jti, roles, sin permisos ni identificador de cliente.

#### Scenario: Rol desconocido no concede acceso
- **WHEN** un bearer válido con solo un rol desconocido solicita GET `/api/orders?status=PAID`
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; pedidos y stock no cambian

#### Scenario: Rol desconocido no elimina un rol conocido
- **WHEN** un bearer válido con SUPPORT y un rol desconocido consulta GET `/api/orders/{ajeno}` existente
- **THEN** responde HTTP 200, sin modificar pedido ni stock

#### Scenario: Claims permanecen exactos tras login
- **WHEN** una cuenta válida inicia sesión mediante POST `/api/auth/login`
- **THEN** responde HTTP 200 y el access token contiene exactamente los siete claims declarados

### Requirement: Matriz de acceso por endpoint y rol
El sistema SHALL producir la siguiente matriz para peticiones válidas con recursos existentes. Cada columna autenticada representa solo ese rol; propio/ajeno se refiere al cliente vinculado a la cuenta. Todo 401 SHALL usar type `https://api.master-acdc.dev/problems/unauthorized`, todo 403 `https://api.master-acdc.dev/problems/forbidden` y todo 404 `https://api.master-acdc.dev/problems/order-not-found`.

#### Scenario: Matriz de acceso con recursos válidos

| Endpoint | Anónimo | CUSTOMER | CATALOG_MANAGER | SUPPORT | ADMIN |
|---|---|---|---|---|---|
| GET /api/products/A01 | 200 | 200 | 200 | 200 | 200 |
| POST /api/products | 401 | 403 | 201 | 403 | 201 |
| PUT /api/products/{id} | 401 | 403 | 200 | 403 | 200 |
| POST /api/products/{id}/reservations | 401 | 403 | 201 | 403 | 201 |
| POST /api/orders | 401 | 201 | 403 | 403 | 403 |
| GET /api/orders?status=PAID | 401 | 200 | 403 | 200 | 200 |
| GET /api/orders/{propio} | 401 | 200 | 403 | 200 | 200 |
| GET /api/orders/{ajeno} | 401 | 404 | 403 | 200 | 200 |
| POST /api/orders/{propio}/cancellation | 401 | 200 | 403 | 403 | 403 |
| POST /api/orders/{ajeno}/cancellation | 401 | 404 | 403 | 403 | 403 |
| POST /api/orders/{id}/refund | 401 | 403 | 403 | 200 | 200 |
| GET /api/admin/users | 401 | 403 | 403 | 403 | 200 |
| GET /actuator/health | 200 | 200 | 200 | 200 | 200 |
| GET /actuator/metrics | 401 | 401 | 401 | 401 | 401 |

- **GIVEN** datos independientes por combinación, producto A01 existente, versión vigente, stock suficiente, cuenta con cliente para pedidos propios, pedido PENDING para cancelar y PAID para reembolsar
- **WHEN** se ejecuta cada combinación de endpoint y rol de la tabla, con bearer real para las columnas autenticadas
- **THEN** devuelve el HTTP de esa celda y el type indicado para errores; todos los rechazos conservan pedidos, stock y productos
- **AND** cada éxito aplica solo el efecto contratado; las lecturas no modifican datos y metrics no acepta bearer de API

### Requirement: Permisos antes de consultar propiedad
El sistema SHALL rechazar operaciones sin permiso antes de consultar el pedido o su propietario. `order:read-any` SHALL ampliar el alcance de consulta pero no conceder `order:cancel-own`, `order:create` ni `order:refund`.

#### Scenario: Ausencia de permiso de lectura oculta existencia
- **WHEN** CATALOG_MANAGER consulta GET `/api/orders/{id}` propio, ajeno o inexistente
- **THEN** responde siempre HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`, sin consultar la propiedad ni modificar pedidos o stock

#### Scenario: Lectura global no concede cancelación
- **WHEN** SUPPORT solicita POST `/api/orders/{id}/cancellation` para cualquier pedido
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; pedidos y stock no cambian
