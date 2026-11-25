# Tasks

La implementación está bloqueada hasta la revisión humana de la propuesta, la resolución de H10 y H11 para las partes que dependan de ellas y las decisiones de contratos y fronteras indicadas en `design.md`. No asumir sus respuestas.

## 1. Contratos y decisiones previas

- [ ] 1.1 Obtener decisiones humanas sobre H10, H11, la frontera de las 48 horas y la compatibilidad con reembolso de PAID; revisar los deltas antes de implementar. Verificación: revisión humana y `openspec validate handle-stock-shortage-after-payment --strict`.
- [ ] 1.2 Obtener aprobación humana previa de cualquier tabla, puerto entre contextos, cambio de frontera y matriz de seguridad que resulte necesario. Verificación: `ContextMapTest`, `TableOwnershipTest` y, para endpoints, `SecurityMatrixIT`.

## 2. Rotura y efectos

- [ ] 2.1 Implementar la detección y la elección de espera conservando la reserva; verificar con test de aceptación `@DisplayName("Rotura total y el cliente elige esperar la reposición")`.
- [ ] 2.2 Implementar anulación real y Stock liberado de las líneas anuladas, con recuperación e idempotencia; verificar con `@DisplayName("Rotura total y el cliente elige anular")` y pruebas de fallos y repetición de pasarela y liberación.
- [ ] 2.3 Implementar el vencimiento y aviso con reloj inyectado y resolución única frente a la respuesta concurrente; verificar con `@DisplayName("Rotura total sin respuesta en 48 horas")` y tests de frontera y concurrencia.
- [ ] 2.4 Implementar envío parcial y anulación de las líneas faltantes; verificar con `@DisplayName("Rotura parcial con envío de las líneas disponibles")` y test de liberación selectiva.

## 3. Integración

- [ ] 3.1 Ejecutar los cuatro tests de aceptación con pasarela simulada en la frontera y los tests de integración nuevos mediante Failsafe; verificar que pasan sin duplicar anulación ni liberación.
- [ ] 3.2 Ejecutar `bash ./verify-quality.sh --fast` y verificar que finaliza en verde.
