# Proposal

## Why

El catálogo necesita permitir que una persona corrija el nombre y el precio de un producto sin sobrescribir cambios realizados por otra persona mientras el formulario estaba abierto. La API ya persiste una versión optimista, pero todavía no ofrece una operación de edición ni traduce de forma explícita los conflictos de concurrencia a un contrato HTTP estable.

## What Changes

- Añadir `PUT /api/products/{id}` con `name`, `price` y `version` en el cuerpo.
- Validar la versión recibida antes de modificar y separar los dos caminos de `409`: versión del cliente obsoleta y conflicto detectado por el control optimista durante el guardado.
- Devolver `200` con `id`, `name`, `price` y la versión nueva cuando la edición se confirma.
- Devolver `version` también en la respuesta de creación `POST /api/products`.
- Devolver `400` con el contrato de validación existente cuando falte `version`, `404` tipado para productos inexistentes y `409` tipado para modificaciones concurrentes.
- Mantener la validación de `name` y `price` mediante los value objects existentes y conservar sin cambios categoría, status y ordering.
- Trasladar el mapeo de persistencia y web del catálogo a `ProductPersistenceMapper` y `ProductWebMapper`, incluyendo la deuda de creación documentada en `docs/lab-s09.md`.
- Actualizar `docs/lab-s09.md` para que la sección de deuda de mappers conserve únicamente la deuda de ordering.

## Capabilities

### New Capabilities

- `product-editing`: edición HTTP de nombre y precio de productos con control de concurrencia optimista y respuestas de error tipadas.

### Modified Capabilities

- None.

## Impact

- Afecta al contrato REST del catálogo, sus casos de uso y puertos de persistencia.
- Afecta al adaptador JPA del producto y a la configuración de infraestructura, donde se demarcará la transacción con `TransactionTemplate`.
- Añade pruebas unitarias, de integración HTTP y de persistencia para inexistencia, validación, dos PUT secuenciales con versión obsoleta y un conflicto determinista entre la comprobación de versión y el guardado.
- No requiere dependencias nuevas ni cambios en ordering, categoría, status o endpoints de detalle.

## Non-goals

- Editar categoría o status.
- Añadir GET de detalle.
- Usar bloqueo pesimista o retener locks mientras una persona edita.
- Cambiar el ordering.
