# Tasks

## 1. Modelo y caso de uso

- [x] 1.1 Extender el modelo de producto y el puerto de catálogo para transportar la versión, localizar por id y guardar una edición sin modificar SKU, categoría, status ni fecha; verificar con tests unitarios llamados `should_update_name_and_price_when_version_is_current`, `should_reject_when_client_version_is_stale` y `should_preserve_product_when_name_or_price_is_invalid`.
- [x] 1.2 Implementar el caso de uso y sus excepciones de inexistencia y concurrencia, aplicando los value objects antes de cambiar el producto; verificar con tests unitarios llamados `should_throw_product_not_found_when_id_does_not_exist`, `should_throw_concurrent_modification_when_version_is_stale` y `should_reject_when_version_is_missing`, ejecutados con `bash ./mvnw -q -Dtest=*Product*ServiceTest test` y sin `@DisplayName` de escenarios.

## 2. Persistencia y transacción

- [x] 2.1 Mover la conversión de entidad JPA a `ProductPersistenceMapper`, conservar `@Version` y adaptar el repositorio para devolver la versión inicial y la nueva versión tras guardar; verificar con tests unitarios del mapper y una integración de repositorio que compruebe versión y preservación de campos.
- [x] 2.2 Ensamblar la edición con `TransactionTemplate` en configuración de infraestructura y hacer que el adaptador use `saveAndFlush`, traduciendo `ObjectOptimisticLockingFailureException` a la excepción de concurrencia; verificar con una integración determinista que, después de leer y comparar dentro de la transacción, otra conexión incremente la versión por JDBC antes del `saveAndFlush`, y que el resultado sea 409 sin conservar los datos del PUT.

## 3. API web y mappers

- [x] 3.1 Añadir el request y la respuesta de edición y concentrar en `ProductWebMapper` la conversión de petición y resultado, incluyendo la versión en la respuesta de creación; verificar `ProductWebMapperTest` con casos de normalización y versión.
- [x] 3.2 Exponer `PUT /api/products/{id}` y ampliar el manejador de errores para los tipos `product-not-found` y `concurrent-modification`; verificar con tests de integración HTTP cuyos `@DisplayName` sean exactamente `Edición válida del producto`, `Versión del cliente obsoleta`, `Conflicto detectado al guardar`, `Producto inexistente`, `Nombre o precio inválido`, `Versión ausente` y `Producto creado`, comprobando los códigos, tipos ProblemDetail y que los productos rechazados quedan sin cambios.

## 4. Documentación e integración

- [x] 4.1 Actualizar `docs/lab-s09.md` para dejar en la sección de deuda de mappers únicamente ordering; verificar con una inspección automatizada o revisión de diff que no queden las dos entradas de creación del catálogo.
- [x] 4.2 Ejecutar `bash ./verify-quality.sh --fast` y corregir cualquier fallo de especificaciones, Spotless, Checkstyle o tests sin relajar umbrales.
