# Tasks

## 1. Dominio y caso de uso

- [x] 1.1 Añadir el modelo inmutable de balance de stock, la excepción nueva de insuficiencia propia de `inventory.domain` con `available` y la excepción de stock ocupado, manteniendo `inventory.domain` sin dependencias de infraestructura; verificar con tests unitarios llamados `should_reserve_when_units_are_positive`, `should_reject_when_available_is_insufficient` y `should_reject_when_units_are_non_positive`, sin `@DisplayName` de escenarios.
- [x] 1.2 Añadir el puerto y el caso de uso de reserva de un producto que carguen el balance bloqueado, apliquen la reserva en el dominio y devuelvan producto, unidades y disponible restante; verificar con tests unitarios llamados `should_return_remaining_stock_when_reservation_succeeds`, `should_throw_stock_not_found_when_record_is_missing` y `should_throw_insufficient_stock_when_units_exceed_available`, sin `@DisplayName` de escenarios.

## 2. Persistencia y transacción

- [x] 2.1 Implementar `StockJpaRepository.findForUpdateById` con bloqueo pesimista de escritura y hint `jakarta.persistence.lock.timeout=2000`, además del mapper y adaptador de persistencia; verificar con una integración Testcontainers que una reserva válida descuenta exactamente las unidades y devuelve el disponible restante.
- [x] 2.2 Traducir el registro ausente y el timeout concreto del bloqueo a excepciones de dominio sin capturar excepciones genéricas; verificar con tests de adaptador llamados `should_throw_stock_not_found_when_record_is_missing` y `should_translate_lock_timeout_when_database_rejects_lock`, sin `@DisplayName` de escenarios, que el stock queda sin cambios.
- [x] 2.3 Ensamblar el caso de uso con `TransactionTemplate` en `inventory.infrastructure.config`, sin `@Transactional` en domain/application; verificar con una integración transaccional llamada `should_commit_reservation_when_stock_is_available` y otra llamada `should_rollback_when_stock_is_insufficient`, sin `@DisplayName` de escenarios.

## 3. API web

- [x] 3.1 Añadir request, response y mapper web dedicado para `POST /api/products/{id}/reservations`, incluyendo validación de `units` y el disponible restante; verificar `StockReservationWebMapperTest` y el test HTTP `@DisplayName("Reserva directa exitosa")`.
- [x] 3.2 Exponer el endpoint desde el adaptador web de inventory y mapear sus errores a ProblemDetail: `insufficient-stock` con `available` y `stock-not-found`; acotar el `@RestControllerAdvice` al controlador de reservas y manejar solo las tres excepciones de inventory, manteniendo la validación existente sin handlers genéricos; verificar tests HTTP con `@DisplayName("Stock insuficiente")`, `@DisplayName("Stock inexistente")`, `@DisplayName("Unidades ausentes")` y `@DisplayName("Unidades no positivas")`.

## 4. Concurrencia y evidencia

- [ ] 4.1 Añadir el test determinista de 50 reservas concurrentes usando `@SpringBootTest(webEnvironment = RANDOM_PORT)`, un cliente HTTP real, `ExecutorService`, `CountDownLatch` y límites en `await`/`Future.get`; verificar `@DisplayName("Reservas concurrentes en venta flash")` con exactamente 30 respuestas 201, 20 respuestas 409, cero respuestas 503 y stock final 0.
- [ ] 4.2 Añadir el test de bloqueo retenido por otra transacción y comprobar que la API responde 503 en torno a dos segundos, con `Retry-After: 1` y stock sin cambios; verificar `@DisplayName("Stock ocupado")` sin sleeps de aplicación ni awaits sin límite y demostrar el plan B de `SET LOCAL lock_timeout = '2s'` si el hint JPA no acota la espera.
- [ ] 4.3 Ejecutar la misma carga de 50 reservas contra la ruta pesimista y contra `discountIfAvailable`, medir tiempo total y líneas de código relevantes y documentar la comparación en `docs/adr/0007-buscador-y-concurrencia-del-catalogo.md`; verificar la evidencia con el test de carga acotado y una revisión de diff que confirme que ordering no cambia.

## 5. Integración final

- [ ] 5.1 Ejecutar `bash ./verify-quality.sh --fast` y corregir cualquier fallo de especificaciones, formato, Checkstyle, arquitectura o tests sin relajar umbrales; verificar que todos los escenarios anteriores siguen pasando.
