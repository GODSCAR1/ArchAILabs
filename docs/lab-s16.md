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

## 2. Infraestructura común

Commits `c314d72` (identity, ordering, inventory), `9fb7a31` (catalog sin guardianes), `422a8b1` (guardianes de catalog, cambio humano) y la limpieza final.

- `TestcontainersConfiguration`: PostgreSQL `postgres:17-alpine`, Mongo `mongo:8`, Redis `redis:8-alpine` (`@ServiceConnection(name = "redis")`) y WireMock `wiremock/wiremock:3.13.1` con `DynamicPropertyRegistrar` → `shop.payments.base-url`. Todos con `withReuse(true)` (efecto solo si `~/.testcontainers.properties` lo habilita).
- `IntegrationTest`: `@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Import({TestcontainersConfiguration, IntegrationTestBeans})`, los 12 spies que ya compartía `IdentityIntegrationSupport` y un `@BeforeEach` que limpia los cuatro almacenes y la L1 (Caffeine).
- La configuración de test vive en `application-test.properties`: cache activa, job de stock con `PT1H`, estadísticas de Hibernate y `SqlCapture`.

### Medidas tras el paso 2 (`./mvnw clean verify`)

| Medida | Inicial | Tras el paso 2 |
|---|---|---|
| Tests de integración (Failsafe) | 294 | **451** (los `*Test` que levantaban contexto pasan a `*IT`) |
| Tests de Surefire | — | 417 en 22 s, 7 contextos pequeños (`@WebMvcTest`, `@SpringJUnitConfig`) |
| Contextos de Spring en Failsafe | 23 | **1** (`missCount = 1`) |
| Contenedores arrancados | 62 | **4** + Ryuk |
| Tiempo de `./mvnw (clean) verify` | 7 min 54 s | **4 min 44 s** (con `clean`) |
| Tiempo de las clases de Failsafe | — | 216 s, de los que 101,5 s son de `SessionVersionLatencyIT` |
| Clases con H2 / `@MockitoBean` fuera de `@WebMvcTest` / `@DynamicPropertySource` propio | 0 / 1 / 0 | **0 / 0 / 0** |

### Decisiones

| Problema | Decisión |
|---|---|
| `@Sql` se ejecuta en el `beforeEach` de `SpringExtension`, antes del `@BeforeEach` de la base: el truncado borraba el escenario | Fixtures en el `@BeforeEach` de la subclase o al inicio del test, con helpers de parámetros enlazados (`stock(A, 7)`, `order(...)`) que reciben las constantes del test: un solo origen para cada id |
| Truncado dinámico (`information_schema` + `String.join`) señalado por `shop-sql-concatenation`; el bloque `DO ... EXECUTE` que propuso el revisor también concatena SQL y `AGENTS.md` lo prohíbe | Lista blanca: `TRUNCATE` literal en `IntegrationTest` y `IntegrationTestInfrastructureIT` compara los nombres de ese literal con `information_schema.tables`. Una tabla nueva sin limpiar rompe la suite con un mensaje claro |
| Tests de integración con sufijo `*Test` corrían en Surefire y arrancaban otro juego de contenedores en esa JVM | Todo lo que extiende `IntegrationTest` termina en `IT`. Los guardianes los renombró una persona (y `protected-tests.txt`, `security-suppressions.txt`, `AGENTS.md`) |
| El gate de cobertura de ramas de identity se alimentaba de `./mvnw test` | `--fast` ejecuta en Failsafe `AuthFlowIT`, `SessionVersionRedisIT`, `SecurityMatrixIT` (mínimo de 90 casos desde `failsafe-reports`), `ApiSecurityIT`, `MethodAuthorizationIT` y los `*QueryCountIT` de identity/ordering. Tensión con "los `*IT` en `--full`" del paso 7: se resolverá ahí |
| `CatalogStockProjectionSchedulingIT` pedía su propio contexto (job cada segundo, cache desactivada) | Job activo con `PT1H` en el perfil común; el test busca su tarea en `ScheduledTaskHolder` por `toString()` (desde Spring 6.2 `Task` envuelve el runnable en `OutcomeTrackingRunnable`), comprueba el intervalo de la propiedad e invoca `synchronizeStock()` |
| `SessionVersionRedisIT` arrancaba un segundo Redis vacío | Base lógica 1 del Redis común con `FLUSHDB` al empezar |
| `*MigrationIT` necesitan un esquema anterior a V9 | Base PostgreSQL aislada (`DROP IF EXISTS` + `CREATE DATABASE`) en el servidor común y Flyway con versión objetivo |
| `CatalogMongoStructureIT` hacía `drop` de su base | Base Mongo aislada `catalog_structure_test` sobre el `MongoClient` del contexto |
| `OrderTransactionIT` usaba `@MockitoBean CurrentOrderActor` | Actor real: cuenta y cliente insertados y `@WithMockUser(username = <id de la cuenta>)` |

### Hallazgos

- **`ProductCacheMultiInstanceIT` funcionaba por casualidad.** Sus dos instancias recibían `--spring.data.mongodb.uri`, que en Spring Boot 4 no existe (es `spring.mongodb.uri`): acababan en el Mongo 7 de `GlobalMongoTestContainerExtension`, que lo publicaba como propiedad de sistema para toda la JVM. Al retirar la extensión el test habría fallado sin una causa evidente.
- **`ProductReviewQueryIT` era código muerto desde el S10**: abstracta, sin subclases, sobre tablas (`products`, `reviews`) eliminadas en V7. Borrada; las reseñas las cubren `CatalogDetailMongoRepositoryAdapterIT` y `ProductDetailControllerTest`. También se borró `MasterAcdcApplicationIT` (redundante con la base común).
- **El agente respetó el guide frente al revisor**: rechazó el bloque `DO ... EXECUTE` que se le propuso porque `AGENTS.md` prohíbe el SQL concatenado, y paró en cada cambio de la zona protegida (guardianes, `pom.xml`, `verify-quality.sh`, `protected-tests.txt`).
- **Entorno (Windows)**: Smart App Control bloquea los lanzadores de pip sin firmar; `osemgrep` delegaba `--version` en `pysemgrep.exe` y la capa 2 fallaba con `Semgrep ?`. El `~/bin/semgrep` local entra ahora directamente en `semgrep.console_scripts.pysemgrep:main` por `python.exe`; el canario de `verify-quality.sh` confirma que analiza (7 hallazgos).
- **Medir con `clean`**: `verify` sin `clean` deja en `target/test-classes` los recursos borrados (`spring.properties`, `config/`), y `clean` borra un log que se escriba en `target/`. Los logs de medida van fuera de `target/`.
