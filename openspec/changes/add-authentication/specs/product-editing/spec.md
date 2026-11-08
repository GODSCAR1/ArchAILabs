# Spec Delta

## ADDED Requirements

### Requirement: Escrituras de producto autenticadas
El sistema SHALL exigir JWT válido en POST `/api/products` y PUT `/api/products/{id}` antes de los contratos existentes de validación, concurrencia e invalidación. Sus escenarios de escritura SHALL asumir autenticación válida. La autorización se define en add-rbac-and-object-authorization. Las lecturas GET del catálogo SHALL continuar públicas.

#### Scenario: Escritura anónima no modifica catálogo
- **WHEN** se solicita crear o editar un producto sin JWT válido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin modificar productos, cache, pedidos ni stock

#### Scenario: Edición autenticada conserva conflicto de versión
- **WHEN** se solicita PUT de un producto existente con JWT válido y versión obsoleta
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/concurrent-modification`, sin modificar producto, cache, pedidos ni stock
