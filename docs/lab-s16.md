# Lab S16 — suite de integración única: PostgreSQL + MongoDB + Redis + WireMock con Testcontainers

Rama `lab/s16` sobre `main` (tras el merge del S15, `8f10cc9`). Spring Boot 4.1.1, Testcontainers con `@ServiceConnection`.

## Adaptación del enunciado al repo

| Enunciado | `master-acdc` | Decisión |
|---|---|---|
| Outbox de confirmación de pedido (Sección 15/24) | No existe: los pedidos se crean (`PENDING`), se cancelan y se reembolsan; el stock llega a Mongo por polling (`CatalogStockProjectionJob`) | Se crea mínimo con un change de OpenSpec: confirmar pedido → evento en tabla outbox en la misma transacción → relay idempotente que descuenta stock en el catálogo |
| Pasarela de pagos `shop.payments.base-url` | No hay ningún cliente HTTP en `src/main` | Se crea mínima en el mismo change (`RestClient` con timeout configurable); WireMock en los tests |
| Clases que usan H2 "porque era más rápido" | Ninguna (solo un comentario en `SecurityMatrixSupport`) | Nada que eliminar del `pom.xml`; queda la regla ArchUnit para que no vuelva |
| `@DynamicPropertySource` por clase | Ninguno | La dispersión real está en `@Container` por clase, `@Import` de configuraciones propias, `properties =`, `@TestPropertySource` y dos extensiones globales autodetectadas |
| Migración `full_name` | No hay columna `full_name` | Se decide en el paso 4 |

## 1. Línea base

```bash
time ./mvnw verify -Dlogging.level.org.springframework.test.context.cache=DEBUG 2>&1 | tee target/it-baseline.log
grep "cache statistics" target/it-baseline.log | tail -1
grep -cE "Container .* started in" target/it-baseline.log
```

| Medida | Inicial |
|---|---|
| Tests de integración (Failsafe) | **294** en 53 clases `*IT` |
| Contextos de Spring creados (`missCount`) | **23** (`size = 8, maxSize = 8`, `hitCount = 3619`) |
| Contenedores arrancados | **62** (48 declaraciones de `@Container` + los de las extensiones globales) |
| Tiempo de `./mvnw verify` | **7 min 54 s** (incluye calidad, JaCoCo y PIT, no solo los IT) |
| Clases con H2 | 0 |
| Clases con `@MockitoBean` fuera de `@WebMvcTest` | 1 (`OrderTransactionIT` → `CurrentOrderActor`) |
| Clases con `@DynamicPropertySource` propio | 0 |
| Configuración que cambia la clave del contexto | `@Import(CatalogMongoTestConfiguration)` ×4, `@Import(GlobalMongoClientTestConfiguration)` ×2, `@Import({IdentityTestContainers, IdentityTestBeans})`, `@Import(OrderSummaryQueryAdapter)`, `properties =` ×7, `@TestPropertySource` ×2 |

`missCount = 23` con `maxSize = 8` cuenta también los contextos que se recrean tras ser expulsados por el LRU (el parche del S11 para el apagado de los forks), así que no es el número de configuraciones distintas sino el de arranques: es el coste real que paga la suite.
