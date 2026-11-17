# Spec Delta

## MODIFIED Requirements

### Requirement: Permisos explícitos por rol
El sistema SHALL conceder únicamente los permisos de esta tabla y unirlos para cuentas con varios roles. ADMIN SHALL no heredar permisos CUSTOMER.

| Rol | Permisos |
|---|---|
| CUSTOMER | order:create, order:read-own, order:cancel-own, order:confirm-own |
| CATALOG_MANAGER | product:write, stock:reserve |
| SUPPORT | order:read-any, order:refund |
| ADMIN | product:write, stock:reserve, order:read-any, order:refund, user:read, user:manage |

#### Scenario: ADMIN no puede crear pedidos por su rol
- **WHEN** un bearer válido con solo ADMIN solicita POST `/api/orders` con líneas válidas
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`; pedidos y stock no cambian

#### Scenario: Roles combinados suman permisos sin perder restricciones
- **WHEN** una cuenta CUSTOMER y CATALOG_MANAGER con cliente asociado crea un pedido válido con stock disponible
- **THEN** POST `/api/orders` responde HTTP 201; sus permisos incluyen creación de pedidos y reserva directa

## ADDED Requirements

### Requirement: Permiso de confirmación de pedido propio
El sistema SHALL conceder `order:confirm-own` a CUSTOMER y exigirlo para confirmar; los demás roles por sí solos no lo reciben. La lectura global SHALL no ampliar el alcance de esta escritura.

#### Scenario: Matriz de confirmación
- **GIVEN** un pedido PENDING con pago autorizable y un cliente asociado al propietario
- **WHEN** se solicita POST `/api/orders/{id}/confirmation` con cada identidad de la tabla
- **THEN** se obtiene el HTTP indicado; los rechazos conservan pedido y stock y no solicitan pago

| Recurso | Anónimo | CUSTOMER propietario | CUSTOMER ajeno | CATALOG_MANAGER | SUPPORT | ADMIN |
|---|---|---|---|---|---|---|
| Pedido PENDING | 401 | 200 | 404 | 403 | 403 | 403 |

- **AND** 401 usa type `https://api.master-acdc.dev/problems/unauthorized`, 403 `https://api.master-acdc.dev/problems/forbidden` y 404 `https://api.master-acdc.dev/problems/order-not-found`

#### Scenario: Lectura global no permite confirmar un pedido ajeno
- **WHEN** un usuario CUSTOMER+SUPPORT solicita confirmar el pedido PENDING de otro cliente
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; el pedido y el stock no cambian ni se solicita pago
