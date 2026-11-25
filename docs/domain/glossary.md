# Glosario del dominio (lenguaje ubicuo)

Sale del Event Storming del lab S19 (`docs/domain/event-storming.md`). Cada término pertenece a un solo
contexto: si un concepto parece pertenecer a dos, casi siempre son dos conceptos distintos, y aquí aparecen
por separado. La columna *En el código* usa el nombre real, o **—** si el concepto no está implementado.

Al escribir código, tests, specs o commits, usa el término de la primera columna. Si el código usa otro
nombre, ese desajuste ya está apuntado en la columna *En el código*: no inventes un tercero.

## Catálogo (`catalog`)

| Término | Definición | En el código | Contexto |
|---|---|---|---|
| Producto | Artículo que la tienda puede vender, con nombre, SKU y precio de venta | `Product` | Catálogo |
| Producto publicado | Producto visible y vendible en la tienda | `ProductStatus.ACTIVE` | Catálogo |
| Producto retirado del catálogo | Producto que deja de venderse. El motivo (descatalogado, error de precio, alerta) es un dato | `ProductStatus.DISCONTINUED` | Catálogo |
| Alerta de seguridad | Aviso del fabricante que obliga a bloquear un producto y retener los pedidos en curso | — | Catálogo |
| Error de precio | Precio publicado por error y evidente (p. ej. 19 € por 1.900 €). Permite anular pedidos pagados (H1) | — | Catálogo |
| Precio de venta | Precio base publicado de un producto | `Price`; `CatalogPrices.ProductPrice.price` | Catálogo |
| Disponibilidad visible | Lo que el catálogo muestra como disponible. Es una proyección, no la fuente del stock | `CatalogStockProjection` | Catálogo |

## Precios y promociones (`pricing`)

| Término | Definición | En el código | Contexto |
|---|---|---|---|
| Promoción | Rebaja de precio programada en el tiempo (campaña) | — | Precios y promociones |
| Cupón | Código que el cliente aplica para obtener un descuento. Se consume al usarlo | `Coupon` | Precios y promociones |
| Descuento por volumen | Rebaja por comprar varias unidades | `VolumeDiscount` | Precios y promociones |
| Importe del pedido | Total que se cobra: precios de las líneas más reglas de precio | `OrderPricing` (cálculo) | Precios y promociones |
| Dinero | Cantidad con moneda. Pertenece al shared kernel | `Money` | Shared kernel |

## Pedidos (`ordering`)

| Término | Definición | En el código | Contexto |
|---|---|---|---|
| Cliente | Quien compra. En Pedidos solo importan su id y su nombre, no sus credenciales | `CustomerName`, `customerId` | Pedidos |
| Carrito | Selección de productos previa al pedido. No es una venta | — | Pedidos |
| Pedido | Compromiso de compra de un cliente sobre unas líneas. Es el agregado | `Order` | Pedidos |
| Línea de pedido | Producto y cantidad dentro de un pedido | `OrderLine` | Pedidos |
| Pedido creado | Pedido que existe y tiene stock reservado, pero no está pagado | `Order.place` → `PENDING` | Pedidos |
| Pago iniciado | El cliente confirmó la compra y el pedido espera la respuesta de la pasarela | `Order.confirm` → `PAYING` | Pedidos |
| Intento de pago | Cada vez que se pide autorización para un pedido. El importe queda fijado en el primero | `PaymentAttempt`, `payment_attempt` | Pedidos |
| Importe fijado | Importe del primer intento de pago, que los reintentos reutilizan | `OrderPayments.fixedAmount`, `payment_amount` | Pedidos |
| Pedido confirmado | Pedido con el pago autorizado. A partir de aquí se prepara | `Order.registerPayment` → `PAID`, `OrderConfirmed` | Pedidos |
| Pedido cancelado | Pedido anulado antes de expedirse, por el cliente, por falta de pago o por error de precio. Libera su stock | `Order.cancel` → `CANCELLED`, `OrderCancelled` | Pedidos |
| Plazo de desistimiento | 14 días tras la entrega en los que el cliente puede devolver sin motivo. Al vencer, la venta es definitiva | — | Pedidos |

## Inventario (`inventory`)

| Término | Definición | En el código | Contexto |
|---|---|---|---|
| Saldo de stock | Unidades disponibles de un producto según el sistema | `StockBalance.available` | Inventario |
| Reserva | Unidades apartadas **en el sistema** para un pedido creado. No es el apartado físico | `ProductReservation`, `InventoryFacade.reserve` | Inventario |
| Stock liberado | Unidades de una reserva que vuelven a estar disponibles | `InventoryFacade.release` | Inventario |
| Reserva caducada | Reserva de un pedido no pagado que vence por tiempo (H4) | — | Inventario |

## Pagos y riesgo (hoy dentro de `ordering`)

| Término | Definición | En el código | Contexto |
|---|---|---|---|
| Pago autorizado | La pasarela acepta el cargo | `PaymentResult.AUTHORIZED` | Pagos y riesgo |
| Pago rechazado | La pasarela deniega el cargo y el cliente puede reintentar | `PaymentResult.DECLINED`, `PaymentDeclinedException` | Pagos y riesgo |
| Cobro capturado | El dinero autorizado se cobra de verdad. Hoy la autorización hace de cobro | — | Pagos y riesgo |
| Cobro anulado | Devolución del dinero de un pedido que **no** se llegó a entregar (cancelado, sin stock, fraude) | `Order.refund` desde `PAID` (mezclado con reembolso) | Pagos y riesgo |
| Reembolso | Devolución del dinero tras una **devolución** del cliente. Puede ser total, parcial o denegado | `Order.refund` desde `SHIPPED` → `REFUNDED` | Pagos y riesgo |
| Contracargo | El banco del titular retira un cobro, normalmente por fraude | — | Pagos y riesgo |
| Pedido retenido | Pedido confirmado que no se prepara hasta que finanzas lo revisa por riesgo de fraude | — | Pagos y riesgo |

## Facturación, Envíos, Devoluciones y Atención al cliente (sin módulo)

| Término | Definición | En el código | Contexto |
|---|---|---|---|
| Factura | Documento fiscal de una venta confirmada | — | Facturación |
| Factura rectificativa | Factura que corrige o anula otra tras un cobro anulado o un reembolso. Antes, "abono" | — | Facturación |
| Envío | Paquete físico de un pedido, desde la preparación hasta la entrega o la vuelta | `OrderStatus.SHIPPED` (solo el estado) | Envíos |
| Apartado | Unidades separadas **físicamente** en el almacén al preparar. Distinto de *reserva* | — | Envíos |
| Rotura de stock | La estantería no tiene las unidades que el sistema tenía reservadas | — | Envíos |
| Envío expedido | El transportista recogió el paquete | — | Envíos |
| Envío entregado | El cliente recibió el paquete | — | Envíos |
| Envío devuelto al almacén | Paquete que vuelve **sin llegar al cliente** (ausencia, plazo de recogida vencido). Acaba en reenvío o cobro anulado | — | Envíos |
| Devolución | El cliente devuelve un producto que **sí recibió**. Acaba en reembolso | — | Devoluciones |
| Revisión | Inspección del producto devuelto: aprobado (vuelve al stock), reacondicionado o merma | — | Devoluciones |
| Incidencia | Caso abierto por atención al cliente sobre un pedido, envío o devolución | — | Atención al cliente |

## Términos de Identidad (`identity`)

| Término | Definición | En el código | Contexto |
|---|---|---|---|
| Cuenta de usuario | Credenciales y roles de quien entra en la tienda. No es lo mismo que el *cliente* de Pedidos | `UserAccount`, `Role` | Identidad y acceso |
| Cliente registrado | Se creó la cuenta y, por política, el cliente en Pedidos | `Registration` → `CustomerRegistrationFacade` | Identidad y acceso |
| Sesión | Par de access token y refresh token rotado | `AccessToken`, `RefreshToken`, `SessionVersion` | Identidad y acceso |

## Pares que no hay que confundir

| No es lo mismo | Diferencia |
|---|---|
| Reserva / Apartado | Sistema frente a estantería. H5 nace de su desajuste |
| Cobro anulado / Reembolso | Antes o después de que el cliente tenga el producto. Se contabilizan distinto |
| Envío devuelto al almacén / Devolución | El paquete no llegó frente a que el cliente lo devolvió |
| Cliente / Cuenta de usuario | Quién compra (Pedidos) frente a quién se autentica (Identidad) |
| Pedido cancelado / Pedido anulado por error de precio | Lo decide el cliente o el sistema frente a lo decide la tienda por un error propio |
