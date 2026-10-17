# Spec Delta

## ADDED Requirements

### Requirement: Liberación de stock devuelto
El sistema SHALL permitir liberar, en una sola operación atómica, el stock de un conjunto de productos con sus cantidades, devolviendo a cada producto exactamente esas cantidades a su stock disponible. La operación SHALL aceptar cantidades positivas y el stock resultante de cualquier producto SHALL ser la suma del stock previo y la cantidad liberada.

#### Scenario: Liberación de stock de varias líneas
- **GIVEN** el producto A con stock 7 y el producto B con stock 3
- **WHEN** se liberan la línea (A, 3) y la línea (B, 2)
- **THEN** la liberación se aplica y el stock queda A = 10 y B = 5

#### Scenario: Stock liberado vuelve a estar disponible para reservar
- **GIVEN** el producto A con stock 7 y liberada la línea (A, 3)
- **WHEN** se reserva la línea (A, 10)
- **THEN** la reserva se aplica y el stock de A queda en 0, nunca negativo
