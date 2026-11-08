# Spec Delta

## ADDED Requirements

### Requirement: Reserva HTTP autenticada
El sistema SHALL exigir JWT válido en POST `/api/products/{id}/reservations` antes de validar unidades o reservar stock. Los escenarios HTTP de reserva existentes SHALL ejecutarse con esa precondición, conservando resultados, atomicidad y espera máxima. Esta autenticación SHALL no afectar a las operaciones internas de reserva y liberación entre módulos.

#### Scenario: Reserva anónima no llega al inventario
- **WHEN** se solicita POST `/api/products/{id}/reservations` sin JWT válido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin crear pedidos ni modificar stock

#### Scenario: Reserva con JWT conserva contrato de éxito
- **WHEN** se solicita POST `/api/products/{id}/reservations` con JWT válido, units 1 y stock inicial 2
- **THEN** responde HTTP 201 con productId, units 1 y disponible 1; no crea ni modifica pedidos
