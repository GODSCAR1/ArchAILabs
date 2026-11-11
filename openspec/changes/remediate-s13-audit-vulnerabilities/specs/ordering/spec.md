# Spec Delta

## ADDED Requirements

### Requirement: Ordenación restringida del listado de pedidos
GET `/api/orders` SHALL aceptar `sort` solo como un campo de la lista `id`, `customerName`, `lineCount`, `totalUnits`, opcionalmente seguido de ASC o DESC. Sin dirección SHALL usar ASC y sin sort SHALL usar `id ASC`. SHALL añadir id como criterio final estable, sin duplicarlo si ya es el campo solicitado. SHALL mantener permisos, alcance, métricas y clientes sin nombre.

#### Scenario: Ordenación permitida conserva unidades descendentes
- **GIVEN** un bearer SUPPORT vigente y pedidos PAID con distintos totales y empates
- **WHEN** solicita GET `/api/orders?status=PAID&sort=totalUnits%20DESC`
- **THEN** responde HTTP 200 con totales descendentes y empates ordenados por id ASC
- **AND** pedidos y stock no cambian

#### Scenario: Campos permitidos y orden predeterminado
- **GIVEN** un bearer con permiso de lectura y pedidos visibles
- **WHEN** solicita GET `/api/orders?status=PAID` sin sort o con cada campo permitido y direcciones ASC/DESC
- **THEN** responde HTTP 200 con el orden solicitado, usando ASC si se omite dirección e id ASC si se omite sort
- **AND** pedidos y stock no cambian

### Requirement: Rechazo de ordenación arbitraria sin efectos
El sistema SHALL rechazar campos, direcciones y fragmentos de ordenación fuera del contrato con HTTP 400 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-order-sort`, antes de ejecutar la consulta de listado. SHALL no interpretar entrada de usuario como instrucciones y SHALL conservar pedidos y stock.

#### Scenario: Ordenación inyectada se rechaza y conserva pedidos
- **GIVEN** un bearer SUPPORT vigente y pedidos PAID persistidos
- **WHEN** solicita GET `/api/orders?status=PAID` con `sort=totalUnits; drop table orders--`
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-order-sort`
- **AND** todos los pedidos siguen existiendo y conservan datos, el stock no cambia y un listado válido posterior responde HTTP 200

#### Scenario: Campo dirección o fragmento no permitido
- **GIVEN** un bearer con permiso de lectura
- **WHEN** solicita GET `/api/orders?status=PAID` con sort vacío, campo desconocido, dirección distinta de ASC/DESC o fragmentos adicionales
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-order-sort`
- **AND** pedidos y stock no cambian
