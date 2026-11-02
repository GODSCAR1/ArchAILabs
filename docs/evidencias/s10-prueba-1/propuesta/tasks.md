# Tasks

## 1. Modelo agregado y consultas

- [ ] 1.1 Incluir reseñas completas en el documento y adaptar el mapper de persistencia; verificar con test unitario de mapping y límites del modelo.
- [ ] 1.2 Adaptar altas de reseñas con actualización atómica/versionada y rechazo de exceso de tamaño; verificar el escenario `Reseña excede el máximo del documento` con `@DisplayName` exacto y comprobar que el agregado no cambia.
- [ ] 1.3 Adaptar consulta de ficha y consulta paginada al agregado; verificar los escenarios `Consultar ficha con reseñas`, `Consultar ficha sin reseñas`, `Consultar página de reseñas` y `SKU inexistente al consultar reseñas` con `@DisplayName` exacto.

## 2. Migración y estructura documental

- [ ] 2.1 Crear migración idempotente de reseñas existentes a los productos y validación previa de tamaño; verificar con integración Mongo que los recuentos se conservan y que un agregado que excede el máximo detiene el corte.
- [ ] 2.2 Actualizar validador, índices y tests de estructura para quitar la colección independiente y alojar reseñas en el producto; verificar con el test de estructura MongoDB y Spotless.

## 3. Rendimiento e integración

- [ ] 3.1 Ajustar tests de conteo para demostrar una operación de ficha con reseñas y paginación correcta; verificar con los tests de integración de conteo Mongo.
- [ ] 3.2 Actualizar documentación y ADR del modelo para reflejar arrays de reseñas completos y el rechazo al máximo de 16 MB; verificar que modelo y ADR describen el contrato de la spec.
- [ ] 3.3 Ejecutar `bash ./verify-quality.sh --fast` y comprobar que termina en verde.
