# Spec Delta

## Purpose

Gestionar la rotura de stock detectada al preparar un pedido cobrado, respetando la elección del cliente y el tratamiento de las líneas disponibles.

## ADDED Requirements

### Requirement: Elección del cliente ante rotura total
Al detectar durante la preparación que faltan todas las unidades de un pedido cobrado, el sistema SHALL registrar Rotura de stock detectada y ofrecer al cliente esperar la reposición o anular.

#### Scenario: Rotura total y el cliente elige esperar la reposición
- **GIVEN** un pedido cobrado con todas sus líneas reservadas y ninguna unidad disponible físicamente
- **WHEN** se registra Rotura de stock detectada y el cliente elige esperar la reposición
- **THEN** el pedido queda a la espera de reposición, sin Cobro anulado ni Stock liberado de sus líneas
- **AND** el vencimiento de las 48 horas ya no anula el pedido

#### Scenario: Rotura total y el cliente elige anular
- **GIVEN** un pedido cobrado con Rotura de stock detectada en todas sus líneas y la elección ofrecida al cliente
- **WHEN** el cliente elige anular
- **THEN** se produce Cobro anulado por el pedido y Stock liberado de todas las líneas anuladas

### Requirement: Ausencia de respuesta ante rotura total
Ante una rotura total, el sistema SHALL anular automáticamente el cobro y avisar al cliente si no responde en 48 horas desde que se le avisa de la Rotura de stock detectada; SHALL liberar la reserva de las líneas anuladas.

#### Scenario: Rotura total sin respuesta en 48 horas
- **GIVEN** un pedido cobrado con Rotura de stock detectada en todas sus líneas y la elección ofrecida al cliente
- **WHEN** transcurren 48 horas desde el aviso al cliente de la Rotura de stock detectada sin respuesta
- **THEN** se produce Cobro anulado por el pedido, Stock liberado de todas las líneas anuladas y se avisa al cliente

### Requirement: Tratamiento de rotura parcial
Al detectar durante la preparación que faltan solo algunas líneas de un pedido cobrado, el sistema SHALL registrar Rotura de stock detectada, enviar las líneas disponibles y producir Cobro anulado y Stock liberado de las líneas que faltan, sin preguntar al cliente.

#### Scenario: Rotura parcial con envío de las líneas disponibles
- **GIVEN** un pedido cobrado con líneas reservadas A y B, A disponible físicamente y B ausente
- **WHEN** se registra Rotura de stock detectada para B
- **THEN** se envía A y se produce Cobro anulado y Stock liberado solo de B, sin pedir elección al cliente
