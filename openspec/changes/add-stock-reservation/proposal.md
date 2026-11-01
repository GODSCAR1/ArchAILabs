# Proposal

## Why

Inventory ya reserva stock atómicamente para ordering, pero el carrito y el checkout necesitan un
endpoint directo de reserva por producto. Durante una venta flash, las llamadas concurrentes deben
competir sin vender por encima del stock, esperar indefinidamente ni exponer detalles de bloqueo de
la base de datos al cliente.

## What Changes

- Añadir `POST /api/products/{id}/reservations` en el adaptador web de inventory con un campo
  obligatorio `units` positivo en el cuerpo.
- Devolver `201` con el id del producto, las unidades reservadas y el stock disponible restante
  cuando la reserva tenga éxito.
- Devolver errores tipados `409` por stock insuficiente con la cantidad disponible y dejar el
  stock sin cambios.
- Devolver un error tipado `404` cuando el producto no tenga registro de stock.
- Devolver `503` con `Retry-After: 1` cuando el bloqueo pesimista no pueda adquirirse en dos
  segundos y dejar el stock sin cambios.
- Devolver el contrato de validación existente con `400` cuando falte `units` o sea menor que uno.
- Reservar mediante un bloqueo pesimista de fila y comprobar la disponibilidad después de adquirir
  el bloqueo.
- Añadir tests deterministas de API y concurrencia, incluidas 50 reservas simultáneas de una unidad
  sobre 30 unidades disponibles y un escenario de timeout del bloqueo.
- Comparar la reserva bloqueada con la ruta atómica `discountIfAvailable` que ya usa ordering para
  aportar evidencia al ADR 0007.

## Capabilities

### New Capabilities

Ninguna. El endpoint amplía la capacidad existente de inventory.

### Modified Capabilities

- `inventory`: exponer reservas directas por producto con resultados HTTP tipados, adquisición de
  bloqueo acotada y una respuesta que incluya el stock restante.

## Impact

- Afecta a los puertos y modelos de dominio de inventory, su caso de uso y el adaptador y la
  configuración de persistencia.
- Añade DTOs web de petición y respuesta, mappers dedicados, comportamiento del controlador y
  manejo de `ProblemDetail` en inventory.
- Añade persistencia con bloqueo pesimista sobre el stock existente de PostgreSQL; no se espera
  ninguna migración de esquema.
- Añade cobertura de integración con Testcontainers para validación HTTP, conservación del stock,
  contención y timeout del bloqueo.
- La reserva atómica `discountIfAvailable` existente de ordering permanece sin cambios.

## Non-goals

- Cambiar la ruta de reserva de ordering o su consulta `discountIfAvailable`.
- Liberar reservas, expirarlas o introducir un registro de reservas.
- Cambiar catalog o hacer que catalog participe en el endpoint.
- Añadir bloqueo pesimista a ordering.
