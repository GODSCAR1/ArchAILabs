# Design

## Context

`OrderLine` guarda solo producto y cantidad; `OrderPricing` calcula a partir de líneas con precios proporcionados. `CatalogStockProjectionJob` lee stock de PostgreSQL y actualiza Mongo con `$set`. Los pedidos se reservan en PENDING y cancelación acepta PENDING y PAID. Véase `proposal.md` para el objetivo.

## Goals / Non-Goals

**Goals:** dos transacciones cortas alrededor de HTTP, importe estable y clave estable por intento, outbox durable y reutilización de la proyección existente.

**Non-Goals:** reconciliar autorizaciones inciertas, añadir un scheduler de recuperación de PAYING o modificar la fuente del stock.

## Decisions

### Precio y límites de módulos

`catalog.api` expone lectura por lote de IDs de precio y SKU directamente desde Mongo con proyección, sin `ProductDetailUseCase` ni cache. Un puerto de ordering es implementado por un adaptador que llama a esta API y usa los tipos públicos de pricing. Decisión confirmada: el cálculo usa `OrderPricing.subtotal`, descuentos por volumen existentes y moneda COP; no incorpora cupón ni impuestos adicionales, pues no hay tasa configurada y añadirla queda fuera de este change. Un producto sin precio produce 422 sin iniciar pago. Alternativa descartada: aceptar importe del cliente o consultar la ficha cacheada.

El importe se calcula antes de la transacción 1; esta comprueba propietario y PENDING, usa el importe ya persistido si existe, o guarda el recién calculado si es el primer intento, junto al cambio condicional a PAYING. Los intentos competidores no pueden sobrescribir el importe. La columna nullable conserva pedidos históricos y reintentos; no se recalcula tras rechazo ni timeout.

### Seguridad y estado

POST `/api/orders/{id}/confirmation` usa el decorador `@PreAuthorize("hasAuthority('order:confirm-own')")`; el permiso se propone para CUSTOMER. El actor procede del sub y cada lectura y actualización de escritura limita por cliente. Se prepara `docs/evidencias/s16-confirmacion-protegida.patch` para que una persona modifique `Role` y los guardianes de matriz protegidos, incluyendo anónimo, cinco roles y objeto ajeno. Cancelación, reembolso y filtro reconocen PAYING; cancelación responde 409.

La transacción 1 hace PENDING→PAYING de forma condicional, sin locks durante HTTP. `orders` conserva un contador de intentos con `DEFAULT 0`; la clave es `order-<uuid>-<intento>`. El adaptador `RestClient` llama fuera de transacción a `POST /authorizations` bajo `shop.payments.base-url`, con `shop.payments.timeout` acotando conexión y respuesta y cuerpo `{ "amount": 123.45, "currency": "COP" }`. Respuesta 200 `{ "status": "AUTHORIZED" | "DECLINED" }`. Sin reintentos automáticos. La pasarela debe serializar claves concurrentes, vincular clave e importe, y conservar el resultado de autorización durante toda la vida de reintentos; error 5xx o timeout no garantizan ausencia de cobro. WireMock verifica la clave y el número de solicitudes, pero no demuestra la idempotencia de una pasarela real.

La transacción 2 hace PAYING→PAID condicional e inserta `OrderConfirmed` en la misma transacción. Para rechazo, 5xx o timeout hace PAYING→PENDING conservando importe; solo tras `DECLINED` definitivo incrementa el contador en la misma transacción. Los errores ambiguos conservan contador y clave; ningún fallo escribe evento ni modifica inventario. Si falla el commit tras autorización, se mantiene PAYING; no se intenta un cobro compensatorio. Supuesto explícito del change: al repetir la misma clave la pasarela devuelve el resultado anterior sin otro cobro; los stubs de WireMock modelan ese contrato y verificar un proveedor real queda fuera de alcance. No hay garantía de ausencia de cobro tras un fallo ambiguo. El fallo de registro del outbox se prueba con un spy del puerto añadido a `IntegrationTest`, sin `@MockitoBean` en el test.

### Outbox y proyección

Una migración Flyway aditiva añade importe nullable y contador de intentos con `DEFAULT 0` al pedido, amplía cualquier restricción de estado existente y crea outbox con ID, pedido, IDs de producto y marca de procesado. Un relay programado de ordering, intervalo configurable, reclama eventos con `FOR UPDATE SKIP LOCKED` en transacción; llama a `catalog.api` para sincronizar IDs mediante la lógica compartida con el job existente y marca procesado solo tras éxito. El job permanece como reconciliación. El proceso relee stock PostgreSQL y hace `$set` en Mongo, nunca `$inc`; falta de fila sigue la política existente de cero. Publica invalidación conforme al listener actual tras commit, sin perderla si se reintenta una proyección ya aplicada. Errores dejan evento pendiente; si el proceso cae tras Mongo y antes de confirmar PostgreSQL, la repetición es segura. El relay no debe depender de que el stock haya cambiado para reemitir una invalidación pendiente.

Proyecciones concurrentes sobre un mismo producto pueden terminar fuera de orden: una lectura antigua puede sobrescribir temporalmente otra más reciente. Relay y job escriben el stock leído con `$set`, sin serialización entre ambos; un evento posterior o la reconciliación corrigen el desfase. Las transacciones de relay no abarcan la llamada HTTP de pagos.

### Reglas de arquitectura

- `ordering` usa solo `catalog.api` para acceder a catálogo; no depende de domain/application/infrastructure de catalog.
- `catalog` y `pricing` no dependen de `ordering`; el grafo de módulos permanece acíclico.
- `ordering` usa tipos públicos de pricing a través de infraestructura; application depende únicamente de su dominio.
- `ordering` consume `inventory.api`; inventory nunca depende de ordering. Relay en ordering llama a `catalog.api`.
- Ampliar `ModuleDependenciesTest` sin debilitar reglas existentes; si el guardián o la firma protegida exige cambios, una persona decide.

## Risks / Trade-offs

- [Precio cambia entre creación y confirmación] → se cobra el precio actual del primer intento, no el vigente al crear; comunicarlo en el contrato.
- [Pedidos previos sin importe] → permanecerán con valor nulo hasta el primer intento y usarán entonces precio actual.
- [Caída entre transacciones] → pedido puede quedar PAYING indefinidamente; reconciliación por clave fuera de alcance.
- [Timeout o 5xx tras cobro] → riesgo aceptado para este change: volver a PENDING permite cancelar un pedido cuyo pago pudo autorizarse; la clave evita un segundo cobro solo si la pasarela cumple su contrato, no evita un cobro huérfano. Reconciliación y compensación quedan fuera de alcance.
- [Rechazo definitivo cacheado por clave] → incrementar el contador tras `DECLINED` habilita otra tentativa con clave distinta y el mismo importe.
- [Desfase de proyección] → escrituras concurrentes pueden dejar temporalmente stock antiguo; un evento posterior o el job reconcilian, sin garantía temporal de producción si los servicios están caídos.

## Migration Plan

1. Preparar `docs/evidencias/s16-confirmacion-protegida.patch` y una persona aplica las zonas protegidas de roles y matriz; ajustar la documentación de cache/ADR donde afirma que checkout nunca lee catálogo, conservando la prohibición de cache.
2. Añadir migración expandir: columna de importe nullable, contador con `DEFAULT 0`, restricción de estado aditiva si existe y outbox; actualizar la lista literal de limpieza de `IntegrationTest` y su guardián sin debilitarlos.
3. Desplegar código con APIs y relay, configuración explícita de URL, timeout e intervalo; verificar que el proveedor cumple el contrato idempotente.
4. Rollback requiere detener confirmaciones, drenar outbox y resolver manualmente PAYING y pagos inciertos antes de volver a una versión que no entiende ese estado; conservar las columnas y registros para recuperación.
