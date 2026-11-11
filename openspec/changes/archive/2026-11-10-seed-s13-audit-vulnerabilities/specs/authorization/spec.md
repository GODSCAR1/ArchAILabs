# Spec Delta

## ADDED Requirements

### Requirement: Matriz de acceso para recuperación y exports
El sistema SHALL ampliar la matriz vigente con las filas siguientes para peticiones válidas y datos independientes por combinación. Los rechazos SHALL conservar cuentas, versiones, familias, archivos, pedidos y stock; 401 SHALL usar type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer; 403 SHALL usar type `https://api.master-acdc.dev/problems/forbidden`.

#### Scenario: Matriz de recuperación y descarga administrativa

| Endpoint | Anónimo | CUSTOMER | CATALOG_MANAGER | SUPPORT | ADMIN |
|---|---|---|---|---|---|
| POST /api/auth/password-reset/request | 202 | 202 | 202 | 202 | 202 |
| POST /api/auth/password-reset/confirm | 204 | 204 | 204 | 204 | 204 |
| GET /api/admin/exports?name=pedidos.csv | 401 | 403 | 403 | 403 | 200 |

- **GIVEN** una cuenta de recuperación habilitada distinta del actor, token vigente para confirmar, un export existente y bearer vigente en las columnas autenticadas
- **WHEN** se ejecuta cada combinación de endpoint y rol de la tabla
- **THEN** devuelve el HTTP de la celda y el type indicado en rechazos; ningún rechazo cambia cuentas, versiones, familias, archivos, pedidos ni stock
- **AND** request solo notifica y registra la solicitud; confirm solo cambia las credenciales y el estado de recuperación y sesión de su destinatario; la descarga no cambia datos; pedidos y stock permanecen sin cambios en todas las combinaciones
