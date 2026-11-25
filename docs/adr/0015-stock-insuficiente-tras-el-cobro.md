# ADR 0015 — Stock insuficiente tras el cobro

- Estado: aceptado (lab S19, 2026-11-24)
- Decisión de negocio: **Oscar Torrens, dueño del negocio**, en el Event Storming del 2026-11-24
- Responsables del hotspot H5: Marta (almacén), Jorge (finanzas), Andrés (atención al cliente)
- Spec: change de OpenSpec `handle-stock-shortage-after-payment`

## Contexto

- H5, "¿Qué ocurre si no hay stock después de cobrar?", fue el hotspot más votado (4 de 4 votos).
- Negocio:
  - Marta: "yo no aparto nada hasta que está cobrado" y "el sistema dice que hay y en la estantería no,
    cada semana".
  - Andrés: tiene que llamar a clientes ya cobrados para decirles que no hay producto.
  - Jorge: devolver un cobro cuesta la comisión de la pasarela.
- Código: el sistema ya reserva al crear el pedido (`CreateOrderService` → `InventoryFacade.reserve`), así que
  la rotura tras el cobro solo nace del desajuste entre la reserva del sistema y el apartado físico. Las
  reservas no caducan (H4), `Order.refund` no llama a la pasarela y no existe *Rotura de stock detectada*.

## Decisión

1. La reserva del sistema se mantiene **al crear el pedido**: un pedido no nace sin reserva (como hoy).
2. Si al preparar un pedido cobrado **faltan todas las unidades**, se registra *Rotura de stock detectada* y
   se ofrece al cliente **esperar la reposición o anular**. Si no responde en **48 horas** (contadas desde que se le
   avisa de la rotura), se anula el cobro automáticamente y se le avisa. Si elige esperar, el plazo deja
   de aplicar.
3. Si **faltan solo algunas líneas**, se envía lo disponible y se anula el cobro de las líneas que faltan,
   sin preguntar.
4. Cada anulación libera la reserva de las líneas anuladas.

**Verificación:** un test de aceptación por cada escenario del change `handle-stock-shortage-after-payment`
(rotura total con espera, rotura total con anulación, rotura total sin respuesta en 48 h y rotura
parcial). Hasta que esos tests pasen, el change sigue abierto en `openspec/changes/`.

## Consecuencias

- ✅ El cliente decide en el caso que más le afecta y atención al cliente deja de llamar uno a uno.
- ✅ Un envío parcial no bloquea el resto del pedido.
- ❌ Cada anulación paga la comisión de la pasarela (Jorge), y hoy no existe una anulación real contra
  la pasarela: antes hay que separar *Cobro anulado* de *Reembolso*.
- ❌ Durante 48 horas la tienda retiene el dinero de un pedido que quizá no sirva, y hace falta un reloj
  (planificador) que hoy no existe.
- ❌ La decisión no corrige la causa (el desajuste entre sistema y estantería), solo la gestiona.

## Pendiente (hotspots nuevos)

- H10: si el cliente elige esperar, ¿cuál es el plazo máximo de espera? (responsables: Lucía, Marta).
- H11: ¿la rotura ajusta automáticamente el saldo del sistema o Marta lo ajusta tras recontar? (responsable: Marta).

## Alternativas

- Reservar solo tras cobrar, como hace Marta: descartada, porque multiplica las roturas tras el cobro.
- Anular el cobro siempre y de forma automática: descartada, porque pierde las ventas de clientes que habrían esperado.
- Esperar la reposición sin preguntar: descartada, porque retiene el dinero sin el consentimiento del cliente.
