# Tasks

## 1. Reintento transaccional

- [ ] 1.1 Envolver cada intento de reserva directa en una nueva ejecución de `TransactionTemplate` y repetir indefinidamente solo ante `StockBusyException`; verificar con una prueba de transacción que un timeout seguido de liberación termina con una reserva confirmada y no deja cambios parciales.
- [ ] 1.2 Mantener fuera del bucle los resultados de negocio y los errores de infraestructura no recuperables; verificar con las pruebas existentes de stock insuficiente, producto inexistente y rollback que conservan sus excepciones y sus datos.

## 2. Contrato HTTP y concurrencia

- [ ] 2.1 Retirar la respuesta HTTP `503` de contención de la ruta de reserva directa sin cambiar los ProblemDetail de `409` y `404`; verificar con pruebas web que una reserva que espera el bloqueo devuelve HTTP `201` y no el type `stock-busy`.
- [ ] 2.2 Actualizar la prueba de concurrencia para que las peticiones competidoras no reciban `503`, exactamente las unidades disponibles terminen en `201`, las restantes en `409` con `insufficient-stock`, y el stock final sea cero; usar `@DisplayName` igual a los escenarios de la especificación.

## 3. Verificación

- [ ] 3.1 Ejecutar `bash ./verify-quality.sh --fast` y corregir cualquier fallo de specs, Spotless, Checkstyle o tests sin debilitar guardianes.
- [ ] 3.2 Ejecutar `graphify update .` y confirmar que el grafo refleja la política de reintento implementada.
