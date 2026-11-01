# Proposal

## Why

Las reservas concurrentes mantienen el stock consistente, pero una contención superior al timeout del bloqueo se expone al cliente como `503 Stock busy`. Esto convierte una situación recuperable en un fallo visible y puede rechazar reservas válidas durante picos de demanda.

## What Changes

- Reintentar indefinidamente una reserva cuando la base de datos no permita adquirir temporalmente el bloqueo del stock.
- Ejecutar cada intento con una transacción independiente para no reutilizar una transacción marcada para rollback.
- Eliminar el `503` por contención del contrato HTTP de reservas; el cliente esperará hasta que la operación pueda continuar.
- Mantener `409` para insuficiencia real de stock y conservar la atomicidad de la reserva.

## Capabilities

### New Capabilities

- Ninguna.

### Modified Capabilities

- `inventory`: una contención temporal del stock se recupera automáticamente y no se expone como `503`; la reserva solo termina por un resultado de negocio, como stock insuficiente o producto desconocido.

## Impact

- Afecta al ensamblado de la reserva directa de inventario, al adaptador HTTP y a sus pruebas de concurrencia e integración.
- No añade dependencias ni cambia el esquema de datos.
- Cambia el comportamiento observable: una petición que antes recibía `503` puede permanecer esperando hasta obtener el bloqueo.

## Non-goals

- No cambiar la semántica de insuficiencia de stock, producto desconocido, validación de cantidades ni liberación de stock.
- No ocultar errores permanentes de infraestructura distintos de la contención recuperable.
- No imponer un timeout de espera al cliente ni implementar una reserva asíncrona.
