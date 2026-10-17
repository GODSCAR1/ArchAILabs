# Proposal

## Why

Hoy un cliente puede crear pedidos pero no cancelarlos: una vez reservado el stock, no hay forma de liberarlo, así que el stock queda bloqueado para siempre si el pedido ya no interesa. Este cambio añade la cancelación de pedidos y la devolución del stock reservado a inventario.

## What Changes

- Nuevo endpoint `POST /api/orders/{id}/cancellation` que cancela un pedido propio (cabecera `X-Customer-Id`) y responde 200 con el pedido en estado CANCELLED.
- Nuevo estado CANCELLED en el dominio de pedidos; cancelables: PENDING y PAID. SHIPPED o ya CANCELLED → 409 con type `https://api.master-acdc.dev/problems/order-not-cancellable`.
- Pedido inexistente o de otro cliente → 404 con type `https://api.master-acdc.dev/problems/order-not-found` (sin revelar existencia). Cabecera ausente/no UUID → 400 como en creación. Precedencia: cabecera (400) → existencia/propiedad (404) → estado (409).
- Al cancelar se libera el stock reservado de todas las líneas del pedido (operación nueva de la fachada pública de inventory).
- Concurrencia: dos cancelaciones del mismo pedido; solo una libera stock, la otra recibe 409.
- ArchUnit: reglas nuevas con `.because(...)` y `.because(...)` añadido a las existentes.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `ordering`: cancelación de pedido (endpoint, estados, errores, precedencia, concurrencia).
- `inventory`: operación de liberación de stock devuelto por la cancelación de un pedido.

## Non-goals

- Reembolsos.
- Cancelación parcial de líneas.
- Notificaciones al cliente.
- Outbox / entrega asíncrona de eventos.

## Impact

- `ordering`: dominio (estado CANCELLED, cancelación condicional), puerto de repositorio (consulta por id y propiedad), controller + handler de errores, nuevo listener de evento de dominio `OrderCancelled` en infraestructura que llama a inventory.
- `inventory`: fachada pública con operación de liberación; repositorio con incremento de stock.
- Base de datos: sin cambios de esquema (estado `VARCHAR(16)` ya admite CANCELLED).
- ArchUnit: `ArchitectureTest` con `.because(...)` en todas las reglas.
