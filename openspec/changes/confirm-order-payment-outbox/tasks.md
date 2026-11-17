# Tasks

Todos los escenarios nuevos se trazan con `@DisplayName` igual al título exacto del escenario; los IT usan `IntegrationTest` y WireMock, sin `@Transactional` en la clase.

## 1. Contratos y datos

- [x] 1.1a Preparar `docs/evidencias/s16-confirmacion-protegida.patch` solo con el permiso `order:confirm-own` para CUSTOMER en `Role`; comprobar con `git apply --check` y una persona aplica el parche.
- [ ] 1.1b Tras el grupo 2, preparar `docs/evidencias/s16-matriz-confirmacion.patch` con la matriz de `SecurityMatrixIT`/`SecurityMatrixSupport` para anónimo, los cinco roles y pedido ajeno; una persona aplica el parche. Verificar `SecurityMatrixIT` escenario «Matriz de confirmación» y «Lectura global no permite confirmar un pedido ajeno» y `MethodAuthorizationIT`.
- [x] 1.2 Añadir reglas de `ModuleDependenciesTest` para catalog.api, ausencia de dependencias inversas y ciclos; ejecutar `ModuleDependenciesTest`.
- [x] 1.3 Añadir migración aditiva de importe, contador de intentos en `orders` con `DEFAULT 0`, PAYING y outbox, incluida limpieza de fixtures; verificar migración con PostgreSQL y `IntegrationTestInfrastructureIT`.
- [x] 1.4 Documentar la lectura de precios sin cache en `docs/caching.md` y ADR relacionados; verificar coherencia con `CatalogMongoArchitectureTest`.

## 2. Precio, estado y pagos

- [ ] 2.1 Añadir consulta por IDs a `catalog.api` y adaptador de pricing con el importe acordado; verificar con `CheckoutPaymentIT` los escenarios «Precio cambia antes del primer intento» y «Falta precio de un producto».
- [ ] 2.2 Implementar transición condicional, importe estable y contador incrementado solo tras `DECLINED` definitivo, cancelación PAYING y filtro; verificar `CheckoutPaymentIT` «Pedido no pendiente no inicia pago», «Pedido ajeno o inexistente no inicia pago», «Cancelación mientras el pago está en curso» y «Listado de pedidos en pago».
- [ ] 2.3 Añadir puerto y `RestClient` con clave `order-<uuid>-<intento>`, timeout, sin reintentos, y control de errores; verificar `CheckoutPaymentIT` «Pago rechazado conserva reserva», «Error de pasarela conserva reserva», «Reintento tras error conserva importe y clave» y «Nuevo intento tras rechazo usa otra clave». En `CheckoutTimeoutIT` verificar «Timeout de pasarela conserva reserva» con timeout corto compartido en `application-test.properties` (p. ej. 500 ms), retardo WireMock claramente mayor y respuesta antes de timeout más margen; comprobar que timeout conserva la clave.
- [ ] 2.4 Asegurar commit atómico PAID y evento y HTTP fuera de transacción; verificar `OrderConfirmationOutboxIT` «Pago autorizado confirma pedido y proyecta stock» y «Fallo al registrar confirmación revierte ambos cambios», inyectando el fallo con un spy del puerto del outbox añadido a los spies de `IntegrationTest`, sin `@MockitoBean` en el test.

## 3. Relay y proyección

- [ ] 3.1 Extraer la sincronización de stock por IDs a `catalog.api`, reutilizando el job y `$set` del stock actual; documentar el riesgo de desorden temporal entre relay y job y su corrección por evento posterior o reconciliación. Verificar con `OutboxRelayIdempotencyIT` y tests existentes del job.
- [ ] 3.2 Implementar relay programado, SKIP LOCKED, reintento durable e invalidación tras proyección incluso en repetición; verificar `OutboxRelayIdempotencyIT` «Confirmación repetida es idempotente» y «Fallo de proyección se recupera», además de concurrencia entre instancias y fallo tras escritura Mongo.
- [ ] 3.3 Verificar con `OrderConfirmationOutboxIT` la entrega real programada en ≤ 5 s y con `CatalogStockProjectionSchedulingIT` que el job de reconciliación permanece; si se usa intervalo corto común de test, justificarlo en el perfil compartido y evitar contextos adicionales.

## 4. Verificación integrada

- [ ] 4.1 Ejecutar cada IT nuevo individualmente con Failsafe según `AGENTS.md` y comprobar los escenarios exactos en los informes: `OrderConfirmationOutboxIT`, `OutboxRelayIdempotencyIT`, `CheckoutPaymentIT` y `CheckoutTimeoutIT`.
- [ ] 4.2 Ejecutar `bash ./verify-quality.sh --fast` y verificar sus resultados; conservar umbrales de JaCoCo y PIT y ejecutar los análisis adicionales que exija el cambio.
