# Spec Delta

## Purpose

Permite conservar una factura por pedido pagado y consultar sus datos exclusivamente desde la cuenta del cliente propietario.

## ADDED Requirements

### Requirement: Factura única e inmutable al pagar
El sistema SHALL emitir exactamente una factura al confirmar por primera vez el pago de un pedido PENDING. SHALL guardar id único, número único `INV-<UUID>`, orderId, totalAmount decimal no negativo con dos decimales e issuedAt en UTC. Importe y fecha SHALL quedar fijados al emitir; cambios posteriores del pedido SHALL no alterar ni borrar la factura.

#### Scenario: Pago emite los datos mínimos de factura
- **GIVEN** un pedido PENDING y una confirmación interna confiable de importe 125.50
- **WHEN** se confirma su pago
- **THEN** quedan PAID y una factura de ese pedido con totalAmount 125.50, número único y fecha de emisión UTC; el stock no cambia

#### Scenario: Cancelación o reembolso conserva la factura
- **GIVEN** un pedido PAID con factura emitida
- **WHEN** el propietario lo cancela mediante POST `/api/orders/{id}/cancellation` o SUPPORT lo reembolsa mediante POST `/api/orders/{id}/refund`
- **THEN** responde HTTP 200 y la factura conserva todos sus datos; la cancelación libera stock una sola vez y el reembolso no modifica stock, según el contrato vigente

### Requirement: Listado propio paginado
GET `/api/invoices` SHALL devolver HTTP 200 con content, page, size, totalElements y totalPages; content SHALL incluir solo facturas propias con id, number, orderId, totalAmount e issuedAt, sin customerId. SHALL aceptar page desde cero y size entre 1 y 100, con valores por defecto 0 y 20; el orden fijo SHALL ser issuedAt descendente e id ascendente. El total SHALL contar solo facturas visibles.

#### Scenario: Listado paginado contiene solo facturas propias
- **GIVEN** 23 facturas propias y 7 ajenas, con fechas repetidas
- **WHEN** CUSTOMER consulta GET `/api/invoices?page=1&size=20`
- **THEN** responde HTTP 200 con 3 facturas propias, page 1, size 20, totalElements 23 y totalPages 2, respetando el orden fijo y sin repetir registros entre páginas sobre datos sin cambios
- **AND** facturas, pedidos y stock no cambian

#### Scenario: Listado vacío y página fuera de rango
- **WHEN** CUSTOMER consulta GET `/api/invoices` sin facturas propias o GET `/api/invoices?page=5&size=20` con 23 propias
- **THEN** responde HTTP 200 con content vacío; en el primer caso page 0, size 20, totalElements 0 y totalPages 0, y en el segundo totalElements 23 y totalPages 2; facturas, pedidos y stock no cambian

#### Scenario: Paginación inválida
- **WHEN** CUSTOMER consulta GET `/api/invoices` con page negativo o no entero, o size fuera de 1..100 o no entero
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-invoice-pagination`; facturas, pedidos y stock no cambian

### Requirement: Detalle limitado por propietario
GET `/api/invoices/{id}` SHALL exigir `invoice:read-own` y devolver HTTP 200 con los mismos campos de una factura del cliente de la cuenta. Un id inválido, inexistente o ajeno SHALL producir HTTP 404 con type `https://api.master-acdc.dev/problems/invoice-not-found`, sin distinguir los casos. La propiedad SHALL limitar la consulta, no filtrarse después de recuperar documentos ajenos.

#### Scenario: Detalle de factura propia
- **WHEN** CUSTOMER consulta GET `/api/invoices/{id}` de una factura propia
- **THEN** responde HTTP 200 con id, number, orderId, totalAmount e issuedAt persistidos, sin customerId; facturas, pedidos y stock no cambian

#### Scenario: Detalle ajeno inexistente o inválido
- **WHEN** CUSTOMER consulta GET `/api/invoices/{id}` con un id ajeno, inexistente o `no-uuid`
- **THEN** responde HTTP 404 con type `https://api.master-acdc.dev/problems/invoice-not-found`; facturas, pedidos y stock no cambian

### Requirement: Identidad de cliente resuelta por petición
El cliente SHALL obtenerse de la cuenta identificada por sub en cada petición, sin cache. Parámetros o cabeceras customerId y X-Customer-Id SHALL no seleccionar propietario. Sin cliente asociado SHALL resultar listado vacío o detalle 404. `order:read-any` SHALL no ampliar el acceso a facturas, tampoco en cuentas multirrol.

#### Scenario: Selectores de cliente no suplantan identidad
- **WHEN** CUSTOMER consulta GET `/api/invoices?customerId=<ajeno>` o GET `/api/invoices/{ajena}` con X-Customer-Id ajeno
- **THEN** el listado responde HTTP 200 solo con facturas propias y el detalle HTTP 404 con type `https://api.master-acdc.dev/problems/invoice-not-found`; facturas, pedidos y stock no cambian

#### Scenario: Cuenta sin cliente no obtiene facturas
- **WHEN** CUSTOMER sin cliente asociado consulta GET `/api/invoices` y GET `/api/invoices/{id}` existente
- **THEN** recibe HTTP 200 con content vacío y totalElements 0, y HTTP 404 con type `https://api.master-acdc.dev/problems/invoice-not-found`, respectivamente; facturas, pedidos y stock no cambian

#### Scenario: Cambio de asociación limita la siguiente lectura
- **WHEN** cambia el cliente asociado a una cuenta entre dos GET `/api/invoices` con el mismo bearer vigente
- **THEN** ambos responden HTTP 200 y el segundo refleja exclusivamente el nuevo cliente; facturas, pedidos y stock no cambian

#### Scenario: Multirrol no obtiene lectura global de facturas
- **WHEN** CUSTOMER+SUPPORT consulta GET `/api/invoices` y GET `/api/invoices/{ajena}`
- **THEN** recibe HTTP 200 solo con facturas propias y HTTP 404 con type `https://api.master-acdc.dev/problems/invoice-not-found`, respectivamente; facturas, pedidos y stock no cambian
