# Spec Delta

## MODIFIED Requirements

### Requirement: Permisos explícitos por rol
El sistema SHALL conceder únicamente los permisos de esta tabla y unirlos para cuentas con varios roles. ADMIN SHALL no heredar permisos CUSTOMER.

| Rol | Permisos |
|---|---|
| CUSTOMER | order:create, order:read-own, order:cancel-own, invoice:read-own |
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

### Requirement: Matriz de acceso a facturas
El sistema SHALL aplicar esta matriz adicional sin alterar las filas existentes de otros recursos. Cada columna autenticada representa solo ese rol; propio y ajeno se refieren al cliente de la cuenta. Los rechazos SHALL conservar facturas, pedidos y stock. `invoice:read-own` SHALL verificarse antes de consultar facturas; los 401 SHALL conservar WWW-Authenticate Bearer.

#### Scenario: Matriz de facturas para anónimo y cinco roles

| Endpoint | Anónimo | CUSTOMER | CATALOG_MANAGER | SUPPORT | ADMIN |
|---|---|---|---|---|---|
| GET /api/invoices | 401 | 200 | 403 | 403 | 403 |
| GET /api/invoices/{propia} | 401 | 200 | 403 | 403 | 403 |
| GET /api/invoices/{ajena} | 401 | 404 | 403 | 403 | 403 |

- **GIVEN** facturas propias y ajenas independientes y bearer vigente para las columnas autenticadas
- **WHEN** se ejecuta cada combinación de la tabla
- **THEN** responde con el HTTP de su celda; los errores usan `https://api.master-acdc.dev/problems/unauthorized` para 401, `https://api.master-acdc.dev/problems/forbidden` para 403 e `https://api.master-acdc.dev/problems/invoice-not-found` para 404
- **AND** todas las combinaciones conservan facturas, pedidos y stock; los listados 200 no incluyen facturas ajenas

#### Scenario: Falta de permiso precede a existencia y parámetros
- **WHEN** CATALOG_MANAGER, SUPPORT o ADMIN consulta GET `/api/invoices/{id}` propio, ajeno, inexistente o inválido, o GET `/api/invoices?page=-1`
- **THEN** responde HTTP 403 con type `https://api.master-acdc.dev/problems/forbidden`, sin consultar facturas ni modificar facturas, pedidos o stock

#### Scenario: Bearer ausente inválido o revocado no permite consultar facturas
- **WHEN** se solicita GET `/api/invoices` o GET `/api/invoices/{id}` sin bearer, con bearer inválido o con versión de sesión revocada
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y WWW-Authenticate Bearer; facturas, pedidos y stock no cambian
