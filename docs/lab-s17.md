# Lab S17 — Refactor de `ordering` a hexagonal estricta

Decisiones y estructura: ADR `docs/adr/0012-arquitectura-hexagonal.md`. Logs: `docs/evidencias/s17/`.

## Adaptación del enunciado al repo

| Enunciado | `master-acdc` | Decisión |
|---|---|---|
| `OrderService` de 600 líneas, `CheckoutService`, entidad JPA con setters | No existían: `ordering` ya tenía `domain`/`application`/`infrastructure`, un servicio por caso de uso y `@Bean` | Corregir las fugas reales del núcleo (tabla de fugas del ADR 0012) |
| Endpoints `/pay` y `/cancel`; listado por cliente paginado | `/confirmation`, `/cancellation`; `GET /api/orders` lista por estado | Se caracteriza y se mantiene la API real |
| Evento del outbox con tipo y payload | Tabla `order_confirmation_outbox` con `order_id` y `product_ids` | Se caracteriza la fila real (sección «Outbox de confirmación») |
| Plazo de 30 días para reembolsar | No existe | No se añade: sería un cambio de comportamiento |
| Optimistic locking | `OrderJpaEntity` sin `@Version` | No se añade (pediría migración); las actualizaciones condicionales protegen las transiciones |
| `adapter.in/out.*` | Convención `infrastructure.adapters.in/out.*` en todo el repo | Se mantiene; tabla de ubicación en el ADR 0012 |
| `protect-verify.sh` | `scripts/protected-tests.txt` | `OrderApiCharacterizationIT` añadido como guardián |

## Pasos (commits en `lab/s17`)

1. Red de seguridad: `OrderApiCharacterizationIT` (25 tests: 6 endpoints con su caso feliz, 401/403/404,
   `ProblemDetail` de cada error y la fila del outbox) y ejecución `pit-ordering` en el `pom`.
2. Dominio: `Order` como clase `final` con constructor privado, `place`/`restore`, transiciones
   `confirm`/`registerPayment`/`cancel`/`refund` y `pullEvents()`; eventos sellados `OrderCancelled` y `OrderConfirmed`.
3. Puertos y adaptadores: comandos y `OrderDetail`; `PublishOrderEvents` (`OrderEventsAdapter`) y `OrderPayments`
   (`OrderPaymentJdbcAdapter`) sustituyen a `OrderCancelledPublisher`, `OrderConfirmationOutbox`,
   `PaymentTransactions` y `OrderPaymentRepository`.
4. ADR 0012, `AGENTS.md` y comprobación provisional de imports en `verify-quality.sh`.

## Medidas

| Medida | Antes | Después |
|---|---|---|
| Tests de caracterización de la API | — (no existían; se escriben en el paso 1: 25 en verde) | 25 en verde; solo cambió la comparación del orden de las líneas, que nunca estuvo garantizado |
| Fila del outbox tras confirmar | `order_id` + `product_ids` | Idéntica |
| Suite completa (`./mvnw verify`) | Verde: 440 unit + 493 IT, 4 min 23 s | Verde: 457 unit + 518 IT, 4 min 30 s |
| Tests del dominio y de aplicación que necesitan Spring | 0 | 0 |
| Tests unitarios de `ordering` (Maven `Total time`) | 84 tests, 26,2 s | 101 tests, 15,1 s |
| PIT `ordering.domain` + `application` (Maven `Total time`) | 29,4 s (20 s de análisis), 73 mutantes | 35,8 s (25 s de análisis), 92 mutantes |
| Mutation score | 86 % (63/73) | **87 % (80/92)**; cobertura de líneas 99 % |
| Imports de `jakarta.persistence`/`org.springframework`/Jackson en `domain` y `application` | 0 | 0, ahora comprobado por `verify-quality.sh` |

**Lectura.** El enunciado espera que PIT tarde «una fracción» porque parte de un dominio que solo se probaba con
Spring. Aquí el dominio y los servicios ya se probaban sin Spring, así que PIT no podía acelerar: tarda unos
segundos más porque hay un 26 % más de mutantes (el agregado y los puertos tienen más reglas) y un 20 % más de
tests. Lo que mejora es el **dónde**: las reglas de estado que antes estaban en `ConfirmationConfiguration` y en
`ConfirmOrderService` ahora están en el agregado, cubiertas por tests unitarios y por PIT.

## Outbox de confirmación

El «evento» no tiene tipo ni payload explícitos: el tipo lo determina la tabla
`order_confirmation_outbox` y el contenido son `order_id` y `product_ids`.

## Bugs encontrados

- **Orden de las líneas de un pedido no determinista** (encontrado en el paso 3 del refactor; ya existía antes).
  `order_lines` no guarda la posición y `OrderJpaEntity.lines` no tiene `@OrderColumn` ni `@OrderBy`, así que
  Postgres devuelve las líneas según el plan (por ejemplo, por el índice único `(order_id, product_id)`).
  La respuesta de la API y `product_ids` del outbox pueden salir en un orden distinto al de creación.
  `OrderApiCharacterizationIT` pasaba solo cuando el orden aleatorio de los UUID coincidía: se cambió (cambio humano,
  zona protegida) a comparar el array de líneas sin orden, manteniendo el resto estricto. Corrección en un cambio
  aparte: decidir el contrato (orden de creación con una columna de posición, o por `productId`).
