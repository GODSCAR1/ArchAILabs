# product-editing Specification

## Purpose
Permite editar de forma segura los datos comerciales básicos de un producto y hace visible la versión necesaria para que el cliente detecte ediciones concurrentes sin perder cambios.

## Requirements

### Requirement: El nombre y el precio se pueden editar con una versión esperada
El sistema SHALL aceptar `PUT /api/products/{id}` con `name`, `price` y `version`, y SHALL actualizar únicamente el nombre y el precio cuando la versión recibida sea la actual.

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
El sistema SHALL incluir la `version` inicial del producto en la respuesta correcta de `POST /api/products`.

#### Scenario: Producto creado
- **WHEN** un cliente crea un producto válido
- **THEN** el sistema devuelve HTTP 201 con los campos existentes de creación y `version` igual a la versión inicial persistida
