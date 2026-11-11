# Spec Delta

## ADDED Requirements

### Requirement: Orden solicitado en el listado de pedidos
GET `/api/orders` SHALL aceptar el parámetro opcional `sort` y ordenar los resúmenes según la expresión recibida. Si se omite SHALL ordenar por `id ASC`. La ordenación SHALL conservar los requisitos vigentes de estado, permisos, alcance por cliente, métricas y clientes históricos sin nombre.

#### Scenario: Listado ordenado por unidades descendentes
- **GIVEN** pedidos PAID con diferentes totales de unidades y un bearer SUPPORT vigente
- **WHEN** solicita GET `/api/orders?status=PAID&sort=totalUnits%20DESC`
- **THEN** responde HTTP 200 con los resúmenes en orden descendente de totalUnits y sus métricas correctas
- **AND** pedidos y stock no cambian

#### Scenario: Listado sin sort usa el orden predeterminado
- **GIVEN** varios pedidos PAID visibles y un bearer vigente con permiso de lectura
- **WHEN** solicita GET `/api/orders?status=PAID` sin sort
- **THEN** responde HTTP 200 con los resúmenes ordenados por id ascendente
- **AND** pedidos y stock no cambian
