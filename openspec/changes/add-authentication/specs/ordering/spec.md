# Spec Delta

## ADDED Requirements

### Requirement: Autenticación previa a los contratos de pedidos
El sistema SHALL autenticar todas las peticiones `/api/orders` y subrutas antes de aplicar los contratos existentes de creación, cancelación y listado. Sus escenarios de negocio SHALL tener como precondición un JWT válido. La identificación por X-Customer-Id y las precedencias de validación posteriores a la autenticación SHALL permanecer iguales, sin resolver todavía la propiedad desde el token.

#### Scenario: Pedido anónimo se rechaza antes de validar cabecera
- **WHEN** se envía POST `/api/orders` sin JWT y sin X-Customer-Id
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin crear pedidos ni modificar stock

#### Scenario: Pedido autenticado conserva validación de cabecera
- **WHEN** se envía POST `/api/orders` con JWT válido pero sin X-Customer-Id
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/missing-customer-id`, sin crear pedidos ni modificar stock

#### Scenario: Listado y cancelación requieren JWT
- **WHEN** se solicita GET `/api/orders?status=PAID` o POST `/api/orders/{id}/cancellation` sin JWT
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin modificar pedidos ni stock
