# Proposal

## Why

Los pedidos reservan stock al crearse, pero todavía no pueden pagarse ni confirmarse. Se necesita confirmar el pago y propagar el stock reservado al catálogo con entrega recuperable.

## What Changes

- Añadir `POST /api/orders/{id}/confirmation`, protegido por `order:confirm-own`, con propiedad comprobada en la consulta.
- Introducir el estado visible `PAYING`, el importe fijado en el primer intento y una pasarela de pagos idempotente con timeout y clave por intento definitivo.
- Registrar `OrderConfirmed` junto con la transición a `PAID` y proyectar el stock actual mediante un relay de outbox y la proyección de catálogo existente.
- Incorporar `PAYING` al listado y rechazar su cancelación durante el pago.
- Establecer contratos entre módulos y escenarios de integración con la infraestructura compartida.

## Capabilities

### New Capabilities

- `order-payment`: autorización de pago, importe estable, fallos y confirmación con outbox.

### Modified Capabilities

- `ordering`: estado `PAYING`, cancelación y filtrado por estado; la creación ya no es la única transición anterior a PAID.
- `authorization`: permiso y matriz de acceso para confirmar pedidos propios.

## Impact

Se afectan ordering, catalog, pricing, PostgreSQL/Flyway, MongoDB y los tests compartidos de integración. Se preparará `docs/evidencias/s16-confirmacion-protegida.patch` para que una persona aplique los cambios protegidos de permisos y matriz; también se revisará la documentación de cache y del ADR de catálogo.

## Non-goals

- Reconciliar automáticamente pagos inciertos o pedidos atascados en `PAYING`.
- Reintentos automáticos de la pasarela, cambios en la reserva de stock o implementación de reembolsos externos.
- Añadir nuevas caches o modificar los umbrales de calidad.
