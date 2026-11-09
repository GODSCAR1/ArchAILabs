# Proposal

## Why

El cliente necesita consultar las facturas de sus pedidos pagados sin poder acceder a documentos de otros clientes. Actualmente no hay facturas ni un caso de uso que haga la transición a PAID.

## What Changes

- Persistir una factura inmutable por pedido, con identificador, número único, pedido, importe total y fecha de emisión.
- Añadir una confirmación interna de pago PENDING → PAID que emita la factura en la misma transacción, con reintentos idempotentes y protección frente a concurrencia.
- Exponer GET `/api/invoices` paginado y GET `/api/invoices/{id}`, únicamente para el cliente asociado a la cuenta autenticada.
- Proponer `invoice:read-own` para CUSTOMER, sin extender `order:read-any` a facturas; incluir matriz para anónimo, los cinco roles y objeto ajeno.
- Someter a revisión humana los cambios necesarios en `Role` y `SecurityMatrixTest`/`SecurityMatrixSupport`, protegidos por AGENTS.md; su edición no queda autorizada por esta propuesta.

## Capabilities

### New Capabilities

- `invoices`: emisión única y atómica al pagar, listado paginado y detalle propios.

### Modified Capabilities

- `ordering`: confirmación interna e idempotente del pago, sin API pública de transición.
- `authorization`: nuevo permiso CUSTOMER y extensión de la matriz de acceso a las dos consultas de facturas.

## Non-goals

- PDF, envío por correo, impuestos, líneas de factura, abonos, numeración fiscal correlativa y pasarela de pago.
- Endpoint público de pago, consulta global de facturas por soporte/administración y emisión retroactiva para estados PAID sembrados previamente.
- Recalcular precios desde el catálogo o modificar el contrato de creación del pedido.

## Impact

- Código futuro en `ordering` (dominio, aplicación, persistencia PostgreSQL, web y configuración transaccional), una migración Flyway aditiva y tests.
- `identity` conserva la resolución de la cuenta por sub mediante su adaptador de `ordering.api.CurrentOrderActor`; solo se propone ampliar el permiso protegido de CUSTOMER.
- Las líneas de pedido actuales contienen producto y cantidad, sin precio: se propone recibir el importe confirmado como entrada interna confiable del pago, no desde estos GET ni desde precios actuales del catálogo. Este supuesto requiere revisión.
- No hacen falta dependencias nuevas ni cambios de configuración HTTP: `/api/**` ya exige bearer. Umbral PIT vigente: 75 %.

## Review decisions

- Aprobar o ajustar la entrada interna de importe confirmado; aún no existe un productor de confirmaciones de pago. Hasta integrarlo, la transición será invocable solo desde código y sus pruebas, no desde HTTP.
- Aprobar numeración técnica única `INV-<UUID>`, no correlativa, importe decimal con dos decimales y fecha/hora UTC.
- Una persona debe actualizar las zonas protegidas y revisar los guardianes antes de aplicar la parte de seguridad. Si se necesitara cambiar una firma protegida, detener la implementación y pedir decisión.
