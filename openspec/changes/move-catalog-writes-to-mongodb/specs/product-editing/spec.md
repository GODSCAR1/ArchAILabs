# Spec Delta

## MODIFIED Requirements

### Requirement: El nombre y el precio se pueden editar con una versión esperada
El sistema SHALL aceptar `PUT /api/products/{id}` con `name`, `price` y `version`, y SHALL actualizar únicamente el nombre y el precio cuando la versión recibida sea la actual, persistiendo el resultado en el almacén de catálogo seleccionado.

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
El sistema SHALL incluir la `version` inicial del producto en la respuesta correcta de `POST /api/products`, persistiendo la creación en el almacén de catálogo seleccionado.

#### Scenario: Producto creado
- **WHEN** un cliente crea un producto válido
- **THEN** el sistema devuelve HTTP 201 con los campos existentes de creación y `version` igual a la versión inicial persistida

#### Scenario: SKU duplicado
- **WHEN** un cliente crea un producto con un SKU ya existente, incluso si la colisión ocurre concurrentemente al guardar
- **THEN** el sistema devuelve HTTP 409 con el mismo ProblemDetail type actual (`about:blank`) y el producto previamente asociado al SKU permanece sin cambios

## ADDED Requirements

### Requirement: Un selector elige conjuntamente los almacenes de catálogo
El sistema SHALL usar `catalog.store` para seleccionar conjuntamente los adaptadores de lectura y escritura del catálogo, y SHALL activar la infraestructura Mongo si `catalog.store=mongo` o `catalog.migration.enabled=true`.

#### Scenario: Almacén Mongo por defecto
- **WHEN** no se configura `catalog.store` en el estado final del corte
- **THEN** `GET /api/products` y las fichas leen de MongoDB, `POST /api/products` responde HTTP 201 y `PUT /api/products/{id}` responde HTTP 200 escribiendo en MongoDB

#### Scenario: Modo PostgreSQL durante coexistencia
- **WHEN** se configura `catalog.store=postgres` mientras las tablas de catálogo coexisten
- **THEN** las lecturas usan PostgreSQL, `POST /api/products` responde HTTP 201 escribiendo en PostgreSQL y `PUT /api/products/{id}` responde HTTP 200 escribiendo en PostgreSQL

#### Scenario: Migración Mongo habilitada en modo PostgreSQL
- **WHEN** se configura `catalog.store=postgres` y `catalog.migration.enabled=true`
- **THEN** las lecturas y escrituras de catálogo permanecen en PostgreSQL y la infraestructura Mongo está activa para recibir la migración

#### Scenario: Modo Mongo con migración desactivada
- **WHEN** se configura `catalog.store=mongo` y `catalog.migration.enabled=false`
- **THEN** los adaptadores de lectura y escritura usan MongoDB y el job de migración PostgreSQL→Mongo no se ejecuta

### Requirement: La disponibilidad de stock en catálogo es una copia eventualmente consistente
El sistema SHALL reflejar periódicamente el stock disponible en la vista de catálogo, sin convertirla en autoridad para reservar o modificar inventario.

#### Scenario: Actualización periódica de stock
- **WHEN** se ejecuta la sincronización periódica para un producto con fila de stock
- **THEN** `availableStock` refleja el valor de `stock.available` en el último ciclo completado, admitiendo desfase entre ciclos

#### Scenario: Producto sin fila de stock
- **WHEN** se ejecuta la sincronización para un producto sin fila de stock
- **THEN** `availableStock` se establece en cero y la reserva de inventario sigue consultando su fuente de verdad
