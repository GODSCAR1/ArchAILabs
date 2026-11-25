# Proposal

## Why

Una reserva en el sistema no garantiza el apartado físico: la rotura puede descubrirse después de cobrar. El ADR 0015 establece cómo proteger al cliente y tramitar las líneas disponibles sin confundir Cobro anulado con Reembolso.

## What Changes

- Registrar Rotura de stock detectada durante la preparación de un pedido cobrado, manteniendo la reserva al crear el pedido.
- Ante una rotura total, ofrecer esperar la reposición o anular; sin respuesta en 48 horas desde el aviso de la rotura, anular el cobro y avisar.
- Ante una rotura parcial, enviar las líneas disponibles y anular el cobro de las líneas que faltan sin preguntar.
- Cada anulación libera la reserva de las líneas anuladas; el Cobro anulado requiere una operación real de la pasarela y es distinto del Reembolso tras una devolución.

## Capabilities

### New Capabilities

- `stock-shortage-after-payment`: tratamiento de las cuatro ramas de rotura tras el cobro.

### Modified Capabilities

Ninguna en esta propuesta: las incompatibilidades existentes requieren decisión y una posterior revisión de sus especificaciones.

## Impact

Pedidos, Inventario, integración con la pasarela, comunicación al cliente y preparación de envíos. Hoy `Order.refund` mezcla anulación y reembolso, y la pasarela solo autoriza; no existe el flujo de rotura ni una anulación real. La especificación `ordering` permite reembolsar un pedido PAID sin liberar stock, por lo que debe reconciliarse explícitamente con el ADR antes de implementar; no se modifica silenciosamente aquí. Nuevos puertos entre contextos, tablas, fronteras o endpoints requieren las decisiones y revisiones humanas establecidas en el proyecto.

## Non-goals

- Cambiar el momento de reserva, implementar caducidad de reservas, devoluciones o facturación rectificativa.
- Decidir el ajuste del saldo por rotura o un plazo máximo de espera por reposición.

## Preguntas abiertas y bloqueos

- **H10 — Lucía y Marta:** si el cliente elige esperar la reposición, ¿cuál es el plazo máximo de espera?
- **H11 — Marta:** ¿la rotura ajusta automáticamente el saldo del sistema o Marta lo ajusta tras recontar?

Ambas siguen abiertas. No se supone ninguna respuesta; las partes de la implementación que dependan de ellas deben esperar a negocio. También deben acordarse la interacción con los contratos existentes y cualquier cambio de frontera antes de implementar.
