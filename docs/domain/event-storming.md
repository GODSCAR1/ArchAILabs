# Event Storming: tienda online de electrónica (lab S19)

Transcripción del tablero `docs/domain/board/event-storming.excalidraw` (imagen: `board/event-storming.png`).
Tablero y tablas salen de la misma lista de notas: si cambias una nota en el tablero, cámbiala aquí también.

| Campo | Valor |
|---|---|
| Fecha | 2026-11-24, una sesión de unos 40 minutos |
| Alcance | Desde que un producto se publica hasta que se cierra una devolución |
| Formato | Big Picture (pasos 2-4) + Process Modeling del tramo *Pedido creado → Pedido confirmado* (paso 5) |
| Facilitador | Oscar Torrens (no modela) |
| Negocio (simulado por un agente) | Lucía (ventas y catálogo), Andrés (atención al cliente), Marta (almacén y logística), Jorge (finanzas) |
| Desviación del enunciado | Los cuatro roles los respondió un solo agente en una sola sesión, no una sesión por rol. Los desacuerdos se forzaron por rol, pero comparten interlocutor: tómalos como hipótesis que hay que contrastar con negocio real |

**Resultado:** 72 eventos · 5 eventos pivote · 7 ramas alternativas · 11 hotspots · 9 policies en el tramo detallado.

## Leyenda

| Color | Elemento |
|---|---|
| Naranja | Evento de dominio, en pasado |
| Azul | Comando |
| Amarillo pequeño | Actor |
| Lila | Policy ("cuando X, entonces Y") |
| Rosa | Sistema externo |
| Verde | Read model |
| Rojo | Hotspot |
| Amarillo claro grande | Agregado |
| Gris | Reloj (evento disparado por el paso del tiempo) |

En el tablero, cada carril es un bounded context (contorno discontinuo). Dentro de cada carril, la fila de arriba es el camino principal y las de abajo son las ramas. Las líneas verticales rojas son los eventos pivote.

**Marca †:** el tablero Big Picture solo tenía eventos, actores, sistemas y hotspots. Los comandos y las policies marcados con † se completaron al transcribir, fuera del tramo detallado. No los validó negocio: son propuestas para la siguiente sesión.

## Eventos pivote

Producto publicado ┃ Pedido creado ┃ Pedido confirmado ┃ Envío expedido ┃ Envío entregado

## Tabla por flujo

### Flujo principal

| # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
|---|---|---|---|---|---|---|
| 1 | Operario de almacén | Registrar entrada de mercancía † | Saldo de stock | Mercancía recibida del proveedor | — | Inventario |
| 2 | Gestor de catálogo | Dar de alta producto † | Producto | Producto dado de alta | — | Catálogo |
| 3 | Gestor de catálogo | Fijar precio de venta † | Producto | Precio de venta fijado | — | Catálogo |
| 4 | Gestor de catálogo | Publicar producto † | Producto | **Producto publicado** ┃ | — | Catálogo |
| 5 | Gestor de catálogo | Programar promoción † | Promoción | Promoción programada | — | Precios y promociones |
| 6 | Gestor de catálogo | Emitir cupón † | Cupón | Cupón emitido | — | Precios y promociones |
| 7 | ⏱ Reloj (inicio de la promoción) | Aplicar promoción † | Promoción | Precio rebajado | Al rebajar un precio, mostrarlo en el catálogo † | Precios y promociones |
| 8 | Cliente | Registrarse † | Cuenta de usuario | Cliente registrado | Al registrarse, dar de alta al cliente en Pedidos † | Identidad y acceso |
| 9 | Cliente | Añadir al carrito † | Carrito | Producto añadido al carrito | — | Pedidos |
| 10 | Cliente | Crear pedido | Pedido | **Pedido creado** ┃ | P1 · Al crear un pedido, reservar su stock | Pedidos |
| 11 | policy P1 | Reservar stock | Saldo de stock | Stock reservado | — | Inventario |
| 12 | Cliente | Confirmar compra | Pedido | Pago iniciado | P2 · Al iniciar el pago, fijar el importe | Pedidos |
| 13 | policy P2 | Fijar importe | Pedido (intento de pago) | Importe del pedido fijado | P3 · Con el importe fijado, pedir la autorización | Pedidos |
| 14 | policy P3 | Autorizar pago | Pasarela de pagos | Pago autorizado | P4 · Si el pago se autoriza, registrarlo en el pedido | Pagos y riesgo |
| 15 | Pasarela de pagos (sistema) | Capturar cobro † | Pasarela de pagos | Cobro capturado | — | Pagos y riesgo |
| 16 | policy P4 | Registrar pago | Pedido | **Pedido confirmado** ┃ | P6 · actualizar la disponibilidad del catálogo · P7 · reglas de riesgo · emitir factura† · preparar el envío† | Pedidos |
| 17 | policy P6 | Proyectar stock | Catálogo (disponibilidad) | Disponibilidad del catálogo actualizada | — | Catálogo |
| 18 | policy: Al confirmar un pedido, emitir factura | Emitir factura † | Factura | Factura emitida | — | Facturación |
| 19 | Operario de almacén | Preparar pedido † | Envío | Pedido preparado | — | Envíos |
| 20 | Transportista (sistema) | Registrar recogida del transportista † | Envío | **Envío expedido** ┃ | Al expedir, avisar al cliente con el seguimiento † | Envíos |
| 21 | policy: Al expedir, avisar al cliente | Notificar expedición † | Proveedor de correo y SMS | Cliente notificado de la expedición | — | Envíos |
| 22 | Transportista (sistema) | Registrar entrega † | Envío | **Envío entregado** ┃ | Al entregar, abrir el plazo de desistimiento † | Envíos |
| 23 | ⏱ Reloj (14 días) | Cerrar plazo de desistimiento † | Pedido | Plazo de desistimiento vencido | — | Pedidos |

### R1 · Retirada del catálogo y error de precio

| # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
|---|---|---|---|---|---|---|
| 24 | Gestor de catálogo | Retirar producto † | Producto | Producto retirado del catálogo | Al retirar un producto, marcarlo como no disponible en los carritos † | Catálogo |
| 25 | Fabricante o proveedor (sistema) | Bloquear producto por alerta † | Producto | Producto bloqueado por alerta de seguridad | Al bloquear por alerta, retener los pedidos en curso con ese producto † | Catálogo |
| 26 | Gestor de catálogo | Notificar error de precio † | Producto | Error de precio detectado | Ante un error de precio, retirar el producto y anular los pedidos pagados con ese precio † | Catálogo |
| 27 | Gestor de catálogo | Corregir precio † | Producto | Precio corregido | — | Catálogo |
| 28 | Gestor de catálogo | Reactivar producto † | Producto | Producto reactivado en el catálogo | — | Catálogo |
| 29 | policy: Al retirar un producto, marcarlo en los carritos | Marcar línea no disponible † | Carrito | Producto marcado como no disponible en el carrito | Avisar al cliente del producto no disponible † | Pedidos |
| 30 | policy: Avisar al cliente del producto no disponible | Avisar al cliente † | Proveedor de correo y SMS | Cliente avisado de producto no disponible | — | Pedidos |
| 31 | policy: Ante un error de precio, anular los pedidos pagados | Anular pedido † | Pedido | Pedido anulado por error de precio | Al anular un pedido pagado, anular el cobro y liberar el stock † | Pedidos |

### R2 · Pago rechazado y reserva caducada

| # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
|---|---|---|---|---|---|---|
| 32 | policy P3 | Autorizar pago | Pasarela de pagos | Pago rechazado | P5 · El pedido vuelve a pendiente y conserva el importe | Pagos y riesgo |
| 33 | policy P3 | Autorizar pago | Pasarela de pagos | Pago sin respuesta de la pasarela | P5 · El pedido vuelve a pendiente y conserva el importe | Pagos y riesgo |
| 34 | policy P5 | Abandonar intento | Pedido | Cliente invitado a reintentar el pago | — | Pedidos |
| 36 | ⏱ Reloj | Caducar reserva | Saldo de stock | Reserva de stock caducada | P8 · Si la reserva caduca, cancelar el pedido por falta de pago | Inventario |
| 35 | Cliente | Confirmar compra (con intento previo) | Pedido | Pago reintentado | P2 (reutiliza el importe ya fijado) | Pedidos |
| 37 | policy P8 | Cancelar pedido | Pedido | Pedido cancelado por falta de pago | P9 · Cuando un pedido se cancela, liberar su stock | Pedidos |
| 38 | policy P9 | Liberar stock | Saldo de stock | Stock liberado | — | Inventario |

### R3 · Fraude

| # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
|---|---|---|---|---|---|---|
| 40 | policy P7 | Retener pedido | Pedido | Pedido retenido por sospecha de fraude | No preparar el pedido hasta la revisión | Pagos y riesgo |
| 41 | Analista de fraude | Liberar pedido † | Pedido | Pedido liberado tras la revisión | — | Pagos y riesgo |
| 42 | Analista de fraude | Confirmar fraude † | Pedido | Fraude confirmado | Detener la entrega y anular el cobro † | Pagos y riesgo |
| 43 | policy: Detener la entrega y anular el cobro | Detener entrega † | Transportista | Entrega detenida a petición de la tienda | — | Envíos |
| 44 | Banco emisor (vía pasarela) (sistema) | Notificar contracargo † | Pasarela de pagos | Contracargo recibido | Registrar la pérdida si no se puede disputar † | Pagos y riesgo |
| 45 | Contable | Registrar pérdida † | Contabilidad | Pérdida por fraude registrada | — | Pagos y riesgo |

### R4 · Falta de stock

| # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
|---|---|---|---|---|---|---|
| 39 | policy P1 | Reservar stock | Saldo de stock | Stock insuficiente al reservar | Si no hay stock, el pedido no se crea | Inventario |
| 46 | Operario de almacén | Notificar rotura de stock † | Envío | Rotura de stock detectada | Ante una rotura tras el cobro, anular el cobro y avisar al cliente † | Envíos |
| 47 | policy: Al anular o cancelar un pedido pagado, anular el cobro | Anular cobro † | Pasarela de pagos | Cobro anulado | Emitir factura rectificativa si había factura † | Pagos y riesgo |

### R5 · Cancelación por el cliente

| # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
|---|---|---|---|---|---|---|
| 48 | Cliente | Cancelar pedido | Pedido | Pedido cancelado por el cliente | P9 · liberar su stock · anular el cobro si estaba pagado† | Pedidos |

### R6 · Entrega fallida

| # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
|---|---|---|---|---|---|---|
| 49 | Agente de atención | Notificar retraso † | Incidencia | Cliente notificado del retraso | — | Atención al cliente |
| 50 | Transportista (sistema) | Registrar intento fallido † | Envío | Intento de entrega fallido | Tras dos intentos, depositar en punto de recogida † | Envíos |
| 57 | Agente de atención | Abrir incidencia † | Incidencia | Incidencia abierta | — | Atención al cliente |
| 51 | Transportista (sistema) | Depositar en punto de recogida † | Envío | Paquete depositado en punto de recogida | — | Envíos |
| 56 | Transportista (sistema) | Declarar envío perdido † | Envío | Envío declarado perdido | Abrir incidencia † | Envíos |
| 52 | ⏱ Reloj (7 días) | Vencer plazo de recogida † | Envío | Plazo de recogida vencido | Devolver el envío al almacén † | Envíos |
| 53 | Transportista (sistema) | Devolver al remitente † | Envío | Envío devuelto al almacén | Avisar a atención al cliente para ofrecer reenvío o reembolso † | Envíos |
| 54 | Cliente | Solicitar reenvío † | Envío | Reenvío solicitado | — | Envíos |
| 55 | Operario de almacén | Reenviar pedido † | Envío | Pedido reenviado | — | Envíos |

### R7 · Devolución

| # | Actor o disparador | Comando | Agregado o sistema | Evento | Política que dispara | Contexto |
|---|---|---|---|---|---|---|
| 58 | Cliente | Solicitar devolución † | Devolución | Devolución solicitada | — | Devoluciones |
| 59 | Agente de atención | Aceptar devolución † | Devolución | Devolución aceptada | Enviar la etiqueta de devolución † | Devoluciones |
| 60 | policy: Enviar la etiqueta de devolución | Enviar etiqueta † | Proveedor de correo y SMS | Etiqueta de devolución enviada al cliente | — | Devoluciones |
| 61 | Transportista (sistema) | Registrar recogida de la devolución † | Devolución | Paquete de devolución recogido por el transportista | — | Devoluciones |
| 62 | Operario de almacén | Recibir devolución † | Devolución | Devolución recibida en almacén | — | Devoluciones |
| 63 | Operario de almacén | Revisar devolución † | Devolución | Devolución aprobada en la revisión | Reembolsar y reincorporar el producto al stock † | Devoluciones |
| 64 | Operario de almacén | Revisar devolución † | Devolución | Devolución rechazada en la revisión | Decidir reembolso parcial o denegado † | Devoluciones |
| 65 | policy: Reembolsar y reincorporar el producto | Reembolsar † | Pasarela de pagos | Reembolso emitido | Emitir factura rectificativa † | Pagos y riesgo |
| 69 | policy: Reembolsar y reincorporar el producto | Reincorporar al stock † | Saldo de stock | Producto reincorporado al stock | — | Inventario |
| 70 | Operario de almacén | Enviar a reacondicionado † | Devolución | Producto enviado a reacondicionado | — | Devoluciones |
| 66 | Contable | Reembolsar parcialmente † | Pasarela de pagos | Reembolso parcial emitido | Emitir factura rectificativa † | Pagos y riesgo |
| 67 | Contable | Denegar reembolso † | Devolución | Reembolso denegado | — | Pagos y riesgo |
| 68 | policy: Emitir factura rectificativa | Emitir factura rectificativa † | Factura | Factura rectificativa emitida | — | Facturación |
| 71 | Operario de almacén | Dar de baja como merma † | Devolución | Producto dado de baja como merma | — | Devoluciones |
| 72 | policy: Con el reembolso resuelto, cerrar la devolución | Cerrar devolución † | Devolución | Devolución cerrada | — | Devoluciones |

## Process Modeling: de *Pedido creado* a *Pedido confirmado*

Gramática completa, contrastada con `ordering` e `inventory`. La columna *Código* dice dónde vive cada pieza hoy.

| Disparador | Comando | Read model | Agregado o sistema | Evento(s) | Policy que dispara | Código |
|---|---|---|---|---|---|---|
| 🟨 Cliente | **Crear pedido** | Carrito con disponibilidad del catálogo | Pedido | `Pedido creado` | P1 · Al crear un pedido, reservar su stock | `PlaceOrderCommand → Order.place` |
| 🟪 P1 | **Reservar stock** | Saldo de stock por producto | Saldo de stock (Inventario) | `Stock reservado` / `Stock insuficiente al reservar` | Si es insuficiente, el pedido no nace | `StockReserver → InventoryFacade.reserve` |
| 🟨 Cliente | **Confirmar compra** | Resumen del pedido | Pedido | `Pago iniciado` | P2 · Al iniciar el pago, fijar el importe | `ConfirmOrderCommand → Order.confirm` |
| 🟪 P2 | **Fijar importe** | Precios vigentes y reglas de precio | Pedido (intento de pago) | `Importe del pedido fijado` | P3 · Con el importe fijado, pedir la autorización | `OrderPayments + OrderAmountCalculator` |
| 🟪 P3 | **Autorizar pago** | Intento de pago | Pasarela de pagos | `Pago autorizado` / `Pago rechazado` / `Pago sin respuesta de la pasarela` | P4 / P5 | `PaymentGateway.authorize` |
| 🟪 P4 | **Registrar pago** | — | Pedido | `Pedido confirmado` | P6, P7 | `Order.registerPayment → OrderConfirmed` |
| 🟪 P5 | **Abandonar intento** | Intento de pago | Pedido | `Cliente invitado a reintentar el pago` | — (el cliente vuelve a Confirmar compra: Pago reintentado) | `abandonAttempt → PENDING` |
| 🟪 P6 | **Proyectar stock** | — | Catálogo (disponibilidad) | `Disponibilidad del catálogo actualizada` | — | `outbox → OrderConfirmationRelay` |
| ⏱ Reloj | **Caducar reserva** | Pedidos pendientes con antigüedad | Saldo de stock | `Reserva de stock caducada` | P8 · Cancelar el pedido por falta de pago | `no existe` |
| 🟪 P8 | **Cancelar pedido** | — | Pedido | `Pedido cancelado por falta de pago` | P9 · Liberar su stock | `no existe (Order.cancel sí)` |
| 🟪 P9 | **Liberar stock** | — | Saldo de stock | `Stock liberado` | — | `OrderCancelledListener → InventoryFacade.release` |
| 🟪 P7 | **Retener pedido** | Reglas de riesgo | Pedido | `Pedido retenido por sospecha de fraude` | No preparar hasta la revisión | `no existe (fuera del tramo)` |

### Policies del tramo

| Id | Policy | Evento de origen |
|---|---|---|
| P1 | Al crear un pedido, reservar su stock | Pedido creado |
| P2 | Al iniciar el pago, fijar el importe | Pago iniciado / Pago reintentado |
| P3 | Con el importe fijado, pedir la autorización | Importe del pedido fijado |
| P4 | Si el pago se autoriza, registrarlo en el pedido | Pago autorizado |
| P5 | Si el pago se rechaza o la pasarela no responde, el pedido vuelve a pendiente y conserva el importe | Pago rechazado / Pago sin respuesta de la pasarela |
| P6 | Al confirmar, actualizar la disponibilidad que ve el catálogo | Pedido confirmado |
| P7 | Si el pedido cumple las reglas de riesgo, retenerlo | Pedido confirmado |
| P8 | Si la reserva caduca, cancelar el pedido por falta de pago | Reserva de stock caducada |
| P9 | Cuando un pedido se cancela, liberar su stock | Pedido cancelado (por el cliente, por falta de pago, por error de precio) |

**Comprobaciones:**
- Ningún evento sin comando: los 13 eventos del tramo tienen comando. *Pago reintentado* es *Confirmar compra* sobre un pedido PENDING con un intento anterior.
- Ninguna policy sin evento de origen (tabla de arriba).
- P7, P8 y la caducidad de reservas están modeladas pero **no existen en el código**: son trabajo pendiente ligado a H4 y a la retención por fraude.

## Fusiones y separaciones (paso 3)

| Notas originales | Nombre acordado | Motivo |
|---|---|---|
| Ficha de producto publicada | **Producto publicado** | Se publica el producto; la ficha es cómo se ve (Lucía) |
| Pedido realizado | **Pedido creado** | "Realizado" sonaba a pagado y no lo está (Andrés) |
| Paquete entregado al transportista | **Envío expedido** | Marta |
| Producto descatalogado por el proveedor | **Producto retirado del catálogo (el motivo es un dato)** | Lucía |
| Pedido marcado como sospechoso + Pedido retenido para revisión | **Pedido retenido por sospecha de fraude** | Se marca y se retiene a la vez (Jorge) |
| Stock agotado tras el cobro | **Rotura de stock detectada** | Marta |
| Cobro devuelto por falta de stock | **Cobro anulado (≠ Reembolso emitido)** | Anular un cobro antes de entregar y reembolsar tras una devolución se contabilizan distinto (Jorge) |
| Producto revisado tras la devolución + Producto aprobado en la revisión | **Devolución aprobada en la revisión** | Marta |
| Abono contable registrado | **Factura rectificativa emitida** | Jorge |
| Paquete devuelto al almacén | **Separado** en *Devolución recibida en almacén* y *Envío devuelto al almacén* | Dos conceptos con el mismo nombre: uno acaba en reembolso y el otro en reenvío |

Añadidos en la narración y en el paso 5: *Pedido confirmado*, *Cliente notificado de la expedición*, *Envío entregado*, *Envío declarado perdido*, *Plazo de desistimiento vencido*, *Stock liberado*, *Stock insuficiente al reservar*, *Pago iniciado*, *Importe del pedido fijado*, *Pago sin respuesta de la pasarela* y *Disponibilidad del catálogo actualizada*.

## Hotspots

Votación con 3 puntos por persona (Lucía: H8, H1, H5 · Andrés: H5, H7, H2 · Marta: H5, H4, H7 · Jorge: H5, H8, H3). Más votados: **H5** (4), **H7** (2), **H8** (2).

| # | Pregunta | Responsable | Votos | Estado |
|---|---|---|---|---|
| H1 | Si un producto se retira mientras el cliente paga, ¿se respeta el pedido o se anula? ¿Depende del motivo (descatalogado, error de precio, alerta de seguridad)? | Lucía (ventas), Andrés (atención) | 1 | abierto |
| H2 | ¿El reembolso se emite al recibir la devolución en almacén o al aprobar la revisión? ¿Cambia si es arrepentimiento, defecto o no entrega? | Jorge (finanzas), Andrés (atención) | 1 | abierto |
| H3 | Si el producto devuelto llega dañado o incompleto, ¿quién decide el reembolso parcial y con qué criterio? ¿Quién paga el envío de vuelta? | Jorge (finanzas), Marta (almacén) | 1 | abierto |
| H4 | Si el pago se rechaza, ¿cuánto tiempo se guarda el producto para reintentar con otro método? ¿Igual en campaña? | Lucía (ventas), Marta (almacén) | 1 | abierto |
| H5 | ¿Qué ocurre si no hay stock después de cobrar? ¿Reservamos antes de cobrar o reembolsamos automáticamente? | Marta (almacén), Jorge (finanzas), Andrés (atención) | 4 | resuelto: ADR 0015 (decide Oscar, dueño del negocio) |
| H6 | Cuando un envío vuelve por ausencia del cliente, ¿quién paga el reenvío y cuántas veces se reenvía antes de reembolsar? | Jorge (finanzas), Andrés (atención) | 0 | abierto |
| H7 | ¿Hasta cuándo se puede cancelar un pedido y quién asume el coste del envío? | Andrés (atención), Marta (almacén), Jorge (finanzas) | 2 | abierto |
| H8 | ¿Con qué precio se cobra si la tarifa cambia mientras el producto está en el carrito? ¿Sobre qué precio se aplica el cupón? | Lucía (ventas), Jorge (finanzas) | 2 | abierto |
| H10 | Si el cliente elige esperar la reposición tras una rotura, ¿cuál es el plazo máximo de espera? (nace del ADR 0015) | Lucía (ventas), Marta (almacén) | 0 | abierto |
| H11 | Tras una rotura de stock, ¿el saldo del sistema se ajusta automáticamente o Marta lo ajusta tras recontar? (nace del ADR 0015) | Marta (almacén) | 0 | abierto |
| H9 | Si se cancela o se devuelve un pedido con cupón, ¿se devuelve el cupón al cliente? | Lucía (ventas), Jorge (finanzas) | 0 | abierto |

H10 y H11 nacieron al decidir H5 (ADR 0015).

Los hotspots abiertos son decisiones de negocio pendientes, no tareas para un agente (ver `docs/lab-s19.md`).

## Tablero frente a código

Hallazgos del paso 5, usados como evidencias en los ADR 0013-0015:

1. **Reserva lógica frente a apartado físico.** El sistema reserva al crear el pedido (`CreateOrderService` → `InventoryFacade.reserve`), antes de cobrar. El almacén aparta en la estantería después de cobrar. *Rotura de stock detectada* aparece cuando el saldo del sistema no coincide con la estantería (H5).
2. **Las reservas no caducan.** Un pedido PENDING que nunca se paga retiene stock indefinidamente. No existe *Reserva de stock caducada* (H4).
3. **El precio se fija en el primer intento de pago** con la tarifa vigente (`OrderPayments.fixedAmount`), y los reintentos lo reutilizan. No coincide con ninguna de las dos posturas de H8.
4. **No existe *Cobro capturado*:** la autorización hace de cobro.
5. **`Order.refund` mezcla dos conceptos:** desde PAID es un *Cobro anulado* y desde SHIPPED es un *Reembolso emitido*, y ninguno llama a la pasarela.
6. **El cupón no llega al checkout:** `Coupon` vive en `pricing`, pero `PlaceOrderCommand` no lo transporta.
7. **Catálogo lee la tabla `stock` de Inventario** con SQL (`CatalogStockProjectionJob`: `select product_id, available from stock`). Es una dependencia oculta Inventario → Catálogo que no sale en los imports y que ArchUnit no ve.

## Revisión de incoherencias de la transcripción

| Comprobación | Resultado |
|---|---|
| Eventos sin comando | 0 en el tramo detallado. Fuera del tramo, todos tienen comando, pero los marcados con † se completaron al transcribir |
| Policies sin evento de origen | 0 |
| Términos con dos nombres | *Cliente* (Pedidos) frente a *cuenta de usuario* (Identidad); *Reembolso* frente a *Cobro anulado*; *Reserva* frente a *apartado*. Resueltos en `glossary.md` |
| Flechas del mapa en los dos sentidos | Catálogo ↔ Pedidos (precios hacia un lado, disponibilidad hacia el otro). Ver `context-map.md` y el ADR 0014 |
| Dependencias que el mapa no tenía | Catálogo lee la tabla `stock` de Inventario por SQL (hallazgo 7) |
| Contextos sin eventos propios | `exports` (no es un contexto) y Notificaciones (es un adaptador) |
