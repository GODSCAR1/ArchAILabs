# Spec Delta

## Purpose

Permitir el pago idempotente de pedidos propios y propagar de forma recuperable el stock confirmado al catálogo.

## ADDED Requirements

### Requirement: Confirmación y autorización del pago
El sistema SHALL permitir POST `/api/orders/{id}/confirmation` para un pedido propio PENDING con `order:confirm-own`, reservando el estado PAYING antes de solicitar autorización. Una autorización aceptada SHALL finalizar en PAID y registrar un evento de confirmación en la misma transacción.

#### Scenario: Pago autorizado confirma pedido y proyecta stock
- **WHEN** el propietario confirma un pedido PENDING y la pasarela autoriza
- **THEN** responde HTTP 200 con id, status PAID y líneas, sin customerId; existe exactamente un evento de confirmación y el stock reservado no cambia
- **AND** el catálogo refleja el stock actual en un máximo de 5 segundos en la prueba de integración

#### Scenario: Pedido no pendiente no inicia pago
- **WHEN** el propietario confirma un pedido PAYING, PAID o CANCELLED
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/order-not-confirmable`; pedido y stock no cambian y no se solicita autorización

#### Scenario: Pedido ajeno o inexistente no inicia pago
- **WHEN** un cliente confirma un pedido ajeno o inexistente
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/order-not-found`; pedido y stock no cambian y no se solicita autorización

#### Scenario: Fallo al registrar confirmación revierte ambos cambios
- **WHEN** la pasarela autoriza pero falla el registro del evento de confirmación, mediante un spy del puerto de outbox compartido en `IntegrationTest`
- **THEN** no se confirma la transición a PAID ni se registra evento; el stock no cambia y el pedido permanece PAYING

### Requirement: Importe fijado
El sistema SHALL calcular el importe con precios actuales sin cache al primer intento, fijarlo junto con PAYING y reutilizarlo en todos los intentos.

#### Scenario: Precio cambia antes del primer intento
- **WHEN** cambia el precio después de crear el pedido y su propietario lo confirma por primera vez
- **THEN** responde HTTP 200 si se autoriza y el importe corresponde al precio actual; el stock reservado no cambia

### Requirement: Idempotencia por intento
El sistema SHALL usar `order-<uuid>-<intento>` como clave, con un contador persistido en `orders` inicializado a 0. SHALL incrementar el contador solo al registrar un `DECLINED` definitivo; tras 5xx o timeout SHALL conservar contador y clave. La pasarela SHALL garantizar como máximo un cobro por clave y devolver el resultado de una autorización previa.

#### Scenario: Reintento tras error conserva importe y clave
- **WHEN** una autorización devuelve 500, cambia el precio y el propietario reintenta
- **THEN** la primera respuesta es HTTP 502 con type `https://api.master-acdc.dev/problems/payment-unavailable`, conserva PENDING y stock; ambas solicitudes de pago usan la misma clave y el mismo importe y existe como máximo un cobro

#### Scenario: Nuevo intento tras rechazo usa otra clave
- **GIVEN** la pasarela devuelve `DECLINED` para el primer intento y el precio cambia después
- **WHEN** el propietario vuelve a confirmar y la pasarela autoriza
- **THEN** la nueva solicitud usa una clave distinta y el importe fijado en el primer intento; el pedido termina PAID

#### Scenario: Falta precio de un producto
- **WHEN** no existe precio actual para una línea al primer intento de confirmación
- **THEN** responde HTTP 422 con type `https://api.master-acdc.dev/problems/payment-price-unavailable`; el pedido sigue PENDING, el stock no cambia y no se solicita autorización

### Requirement: Fallos de autorización controlados
El sistema SHALL devolver el pedido PAYING a PENDING tras rechazo, error 5xx o timeout de la pasarela, sin reintento automático y sin modificar el stock ni emitir confirmación.

#### Scenario: Pago rechazado conserva reserva
- **WHEN** la pasarela rechaza el pago
- **THEN** responde HTTP 402 con type `https://api.master-acdc.dev/problems/payment-declined`; el pedido sigue PENDING y el stock no cambia

#### Scenario: Error de pasarela conserva reserva
- **WHEN** la pasarela responde 500
- **THEN** responde HTTP 502 con type `https://api.master-acdc.dev/problems/payment-unavailable`; el pedido sigue PENDING y el stock no cambia

#### Scenario: Timeout de pasarela conserva reserva
- **WHEN** la pasarela tarda claramente más que el timeout corto configurado para la prueba
- **THEN** responde HTTP 504 con type `https://api.master-acdc.dev/problems/payment-timeout` antes del timeout más un margen; el pedido sigue PENDING y el stock no cambia

### Requirement: Entrega recuperable de confirmación
El sistema SHALL procesar las confirmaciones pendientes al menos una vez, aplicar al catálogo el stock actual de la fuente de inventario e invalidar la ficha afectada después de la proyección. El fallo SHALL dejar el evento disponible para otro intento.

El relay y el job de reconciliación pueden escribir fuera de orden: el catálogo puede mostrar temporalmente stock anterior hasta que un evento posterior o la reconciliación vuelvan a proyectar el stock actual.

#### Scenario: Confirmación repetida es idempotente
- **WHEN** la misma confirmación se procesa dos veces sin cambios adicionales de inventario
- **THEN** el stock proyectado es idéntico al stock actual y no hay descuento adicional

#### Scenario: Fallo de proyección se recupera
- **WHEN** falla la proyección y posteriormente vuelve a estar disponible
- **THEN** la confirmación sigue pendiente hasta procesarse correctamente y termina reflejando el stock actual
