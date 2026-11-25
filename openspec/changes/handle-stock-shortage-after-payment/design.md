# Design

## Context

La reserva ocurre al crear el pedido; `Order.cancel` emite `OrderCancelled` y la liberación alcanza Inventario mediante un listener. `Order.refund` cambia el estado sin llamar a la pasarela, cuyo puerto solo autoriza. La spec `ordering` conserva un contrato de reembolso desde PAID que debe reconciliarse con el ADR 0015 antes de cambiarlo.

## Goals / Non-Goals

**Goals:** hacer verificable el flujo del ADR sin perder efectos ni confundir anulación y devolución.

**Non-Goals:** resolver H10 o H11, definir endpoints o modificar fronteras sin autorización humana.

## Decisions

- Modelar la rotura, la elección y las transiciones de pedido como comportamiento de dominio; conservar la reserva durante la espera. Alternativa descartada: decidir estados en adaptadores.
- Tratar Cobro anulado como operación real diferenciada del Reembolso y vincularla a las líneas afectadas. Una transición local sin confirmación de la pasarela no demuestra una anulación.
- Diseñar la ejecución recuperable e idempotente de la anulación, liberación y aviso, incluidos reintentos y competencia entre respuesta y vencimiento; evitar duplicar cobros anulados o stock liberado. El detalle de persistencia y de integración requiere revisión humana previa si introduce tablas o puertos entre contextos.
- Utilizar tiempo inyectado para verificar el vencimiento de 48 horas desde el aviso al cliente de la Rotura de stock detectada. El tratamiento de respuestas en la frontera requiere acordar el contrato antes de implementarlo; no se confunde con H10.

## Reglas de arquitectura

- Dominio y aplicación no dependen de infraestructura ni frameworks; el agregado decide transiciones y publica eventos mediante su puerto.
- `ordering` consulta `inventory` solo mediante su API publicada desde infraestructura; `inventory` no depende de `ordering`.
- Los efectos posteriores entre contextos, incluida la liberación, viajan por eventos de dominio; no se accede a persistencia ajena. Todo puerto nuevo o cambio de frontera requiere decisión humana y actualización previa del mapa.

## Risks / Trade-offs

- [H10 y H11 abiertos] → negocio debe decidir antes de implementar cualquier parte dependiente; no inferir el saldo ni la duración de espera.
- [Conflicto con reembolso de PAID] → revisar contratos y delta de `ordering` con una persona antes de implementar; no cambiar silenciosamente el endpoint existente.
- [Fallo o respuesta incierta de pasarela] → identificar operación de forma estable y reconciliar antes de repetir; probar que no se da por completada sin confirmación.
- [Carrera entre elección y reloj] → una sola resolución efectiva y pruebas de concurrencia.

## Migration Plan

Tras aprobación de negocio y fronteras, preparar cambios compatibles mediante expandir-contratar; desplegar el flujo solo cuando persistencia, pasarela y consumidores estén preparados. En caso de retirada, detener nuevas roturas y conservar y reconciliar las operaciones pendientes sin deshacer efectos financieros.
