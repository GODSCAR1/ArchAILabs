# product-editing Specification

## Purpose
Permite editar de forma segura los datos comerciales básicos de un producto y hace visible la versión necesaria para que el cliente detecte ediciones concurrentes sin perder cambios.

## Requirements

### Requirement: El nombre y el precio se pueden editar con una versión esperada
El sistema SHALL aceptar `PUT /api/products/{id}` con `name`, `price` y `version`, y SHALL actualizar únicamente el nombre y el precio cuando la versión recibida sea la actual, persistiendo el resultado en MongoDB.

#### Scenario: Edición válida del producto
- **WHEN** un cliente envía un PUT válido para un producto existente con su versión actual
- **THEN** el sistema devuelve HTTP 200 con `id`, `name` normalizado, `price` y la versión nueva, y categoría y status permanecen sin cambios

#### Scenario: Versión del cliente obsoleta
- **WHEN** un cliente envía dos PUT válidos secuenciales con la misma versión inicial del producto
- **THEN** el primer PUT devuelve HTTP 200, el segundo devuelve HTTP 409 con ProblemDetail type `https://api.master-acdc.dev/problems/concurrent-modification`, y el producto conserva el nombre y precio del primer PUT

#### Scenario: Conflicto detectado al guardar
- **WHEN** la versión del producto cambia después de la comprobación de la versión recibida y antes de guardar una edición válida
- **THEN** el sistema devuelve HTTP 409 con ProblemDetail type `https://api.master-acdc.dev/problems/concurrent-modification`, y el producto no conserva el nombre ni el precio del PUT rechazado

### Requirement: La edición informa de productos inexistentes con un tipo de error estable
El sistema SHALL rechazar una edición cuyo identificador no corresponda a un producto existente.

#### Scenario: Producto inexistente
- **WHEN** un cliente envía un PUT para un identificador de producto desconocido
- **THEN** el sistema devuelve HTTP 404 con ProblemDetail type `https://api.master-acdc.dev/problems/product-not-found`, y ningún producto se crea o cambia

### Requirement: La edición conserva la validación de dominio
El sistema SHALL aplicar la validación existente de los value objects del producto a `name` y `price` antes de cambiar un producto.

#### Scenario: Nombre o precio inválido
- **WHEN** un cliente envía un PUT con un nombre o precio inválido
- **THEN** el sistema devuelve HTTP 400 con el contrato de error de validación existente, y el producto permanece sin cambios

#### Scenario: Versión ausente
- **WHEN** un cliente envía un PUT sin `version`
- **THEN** el sistema devuelve HTTP 400 con el contrato de error de validación existente, y el producto permanece sin cambios

### Requirement: La creación de producto expone su versión inicial
El sistema SHALL incluir la `version` inicial del producto en la respuesta correcta de `POST /api/products`, persistiendo la creación en MongoDB.

#### Scenario: Producto creado
- **WHEN** un cliente crea un producto válido
- **THEN** el sistema devuelve HTTP 201 con los campos existentes de creación y `version` igual a la versión inicial persistida

#### Scenario: SKU duplicado
- **WHEN** un cliente crea un producto con un SKU ya existente, incluso si la colisión ocurre concurrentemente al guardar
- **THEN** el sistema devuelve HTTP 409 con el mismo ProblemDetail type actual (`about:blank`) y el producto previamente asociado al SKU permanece sin cambios

### Requirement: La disponibilidad de stock en catálogo es una copia eventualmente consistente
El sistema SHALL reflejar periódicamente el stock disponible en la vista de catálogo, sin convertirla en autoridad para reservar o modificar inventario.

#### Scenario: Actualización periódica de stock
- **WHEN** se ejecuta la sincronización periódica para un producto con fila de stock
- **THEN** `availableStock` refleja el valor de `stock.available` en el último ciclo completado, admitiendo desfase entre ciclos

#### Scenario: Producto sin fila de stock
- **WHEN** se ejecuta la sincronización para un producto sin fila de stock
- **THEN** `availableStock` se establece en cero y la reserva de inventario sigue consultando su fuente de verdad

### Requirement: La edición invalida la ficha después del commit
El sistema SHALL invalidar la ficha correspondiente después de confirmar una edición válida, propagando la invalidación a las instancias activas sin modificar el contrato HTTP de edición.

#### Scenario: Edición válida invalida la ficha
- **GIVEN** `SKU-CAFE` está cacheado con precio `10000` y su versión actual es `4`
- **WHEN** un cliente edita el producto con versión `4` y precio `12000`
- **THEN** el PUT responde HTTP 200 con el contrato existente y la siguiente lectura de ficha responde HTTP 200 con precio `12000`

#### Scenario: Edición rechazada conserva la ficha cacheada
- **GIVEN** `SKU-CAFE` está cacheado con precio `10000` y su versión actual es `5`
- **WHEN** un cliente envía un PUT con versión obsoleta `4` y precio `12000`
- **THEN** el PUT responde HTTP 409 con ProblemDetail type `https://api.master-acdc.dev/problems/concurrent-modification`, la ficha sigue respondiendo HTTP 200 con precio `10000` y no se publica ninguna invalidación

#### Scenario: Fallo del almacén compartido al invalidar no bloquea las copias locales
- **GIVEN** las instancias A y B tienen cacheado `SKU-CAFE` con precio `10000` y el almacén compartido falla durante la invalidación
- **WHEN** se edita el producto a precio `12000` en A
- **THEN** el PUT responde HTTP 200 y A y B responden la ficha con precio `12000`; las copias locales se invalidan aunque falle el almacén compartido

### Requirement: La proyección de stock invalida solo las fichas modificadas
El sistema SHALL invalidar las fichas de los SKU cuyo stock disponible cambió en una pasada de sincronización y SHALL conservar cacheadas las fichas cuyo stock no cambió.

#### Scenario: Cambio de stock en la proyección
- **GIVEN** `SKU-CAMBIA` está cacheado con stock `3` y `SKU-ESTABLE` con stock `6`
- **WHEN** una pasada cambia el stock de `SKU-CAMBIA` a `4` y mantiene `SKU-ESTABLE` en `6`
- **THEN** la ficha de `SKU-CAMBIA` responde HTTP 200 con stock `4` y la ficha cacheada de `SKU-ESTABLE` no se invalida

#### Scenario: El job ejecuta la sincronización periódica
- **GIVEN** la sincronización periódica está habilitada, `SKU-CAFE` tiene precio `10000`, stock proyectado `2` y el origen informa stock `5`
- **WHEN** la aplicación ejecuta el siguiente ciclo programado
- **THEN** `GET /api/products/SKU-CAFE` responde HTTP 200 con precio `10000` y stock `5`

### Requirement: Escrituras de producto autenticadas
El sistema SHALL exigir JWT válido en POST `/api/products` y PUT `/api/products/{id}` antes de los contratos existentes de validación, concurrencia e invalidación. Sus escenarios de escritura SHALL asumir autenticación válida. La autorización se define en add-rbac-and-object-authorization. Las lecturas GET del catálogo SHALL continuar públicas.

#### Scenario: Escritura anónima no modifica catálogo
- **WHEN** se solicita crear o editar un producto sin JWT válido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin modificar productos, cache, pedidos ni stock

#### Scenario: Edición autenticada conserva conflicto de versión
- **WHEN** se solicita PUT de un producto existente con JWT válido y versión obsoleta
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/concurrent-modification`, sin modificar producto, cache, pedidos ni stock
