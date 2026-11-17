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

## 3. Flujos que cruzan almacenes

Change de OpenSpec `confirm-order-payment-outbox` (commits `8b488ab` propuesta, `d255c44` grupo 1, `9bc7973` implementación).

### Decisiones de diseño

| Pregunta | Decisión |
|---|---|
| ¿Quién es dueño del stock del catálogo? | PostgreSQL. El evento `OrderConfirmed` solo avisa: el relay relee el stock actual y hace `$set` en Mongo (idempotente por diseño). `CatalogStockProjectionJob` queda como reconciliación (`PT1H`) y comparte la proyección por ids |
| ¿De dónde sale el importe? | Precio actual del catálogo al confirmar (`catalog.api.CatalogPrices`, Mongo sin cache) y `OrderPricing.subtotal` (descuento por volumen, COP, sin cupón ni impuestos: no hay tasa configurada). Se fija en el primer intento (`payment_amount`, `coalesce`) |
| ¿Cómo no cobrar un pedido cancelado durante el pago? | Estado intermedio `PAYING`: no cancelable (409). Rechazo, 5xx y timeout vuelven a `PENDING` |
| ¿Cómo evitar el doble cobro? | `Idempotency-Key: order-<uuid>-<intento>`; el contador solo sube tras `DECLINED` (definitivo), nunca tras 5xx o timeout (ambiguos) |
| ¿Dónde va la llamada HTTP? | Fuera de transacción: T1 `PENDING→PAYING` + importe, HTTP, T2 `PAYING→PAID` + evento en `order_confirmation_outbox` (o vuelta a `PENDING`) |
| Riesgos aceptados | Caída entre T1 y T2 deja `PAYING`; cobro huérfano tras fallo ambiguo; proyecciones fuera de orden (consistencia eventual, corrige el siguiente evento o el job). Reconciliación con la pasarela fuera de alcance |

### Tests del enunciado

| Test del lab | En el repo |
|---|---|
| `OrderConfirmationOutboxIT` | Nuevo: evento en la misma transacción que `PAID` y stock proyectado en Mongo (≤ 5 s). `OrderConfirmationAtomicIT` añade el fallo del outbox con spy (queda `PAYING`, sin evento) |
| `OutboxRelayIdempotencyIT` | Nuevo: procesar dos veces el mismo evento deja el mismo stock; un fallo de proyección deja el evento pendiente |
| `ProductCacheIT` | Ya cubierto por `CatalogProductCacheEditingIT` (segunda lectura desde la cache, invalidación tras el commit de la edición) y `CatalogStockProjectionCacheInvalidationIT` (invalidación por proyección de stock); no se duplica |
| `CheckoutPaymentIT` | Nuevo: autorizado → `PAID`; rechazado → `PENDING` (402) y nuevo intento con otra clave; 500 → `PENDING` (502), reintento con la misma clave e importe |
| `CheckoutTimeoutIT` | Nuevo: `shop.payments.timeout=PT0.5S` en el perfil test, WireMock con retardo mayor, 504 antes de timeout + margen |
| `AuthFlowIT` | Ya migrado a `IntegrationTest` en el paso 2 |

Más la matriz de seguridad («Matriz de confirmación» y «Lectura global no permite confirmar un pedido ajeno», cambio humano).

### Hallazgos

- **OpenCode (GPT-6.1 Terra) no completaba el apply**: cerraba el turno tras cada pieza. Causa del guide: `AGENTS.md` decía "antes de terminar cualquier tarea: `verify-quality.sh --fast` y pega su salida", y el modelo lo aplicaba a cada casilla de `tasks.md` (verify de minutos, salida enorme en el contexto, fin de turno). Se cambió a "antes de dar por terminado el encargo de la persona". Quitó el verify prematuro; el modelo siguió parando sin motivo y se avanzó con "continúa". Otra causa: frases del design como "requiere aceptación explícita" funcionan como cables trampa; conviene cerrarlas antes del apply.
- **El plugin de Verify es silencioso cuando pasa** y solo corre en `session.idle`: con "continúa" encadenados, durante una hora no se completó ningún verify. El verify final lo lanzó la persona.
- **google-java-format convierte un SQL literal largo en `"…" + "…"`** y `shop-sql-concatenation` lo bloquea: SQL de `jdbc.*` siempre en text block.
- **PMD `CloseResource`** con `HttpClient` del JDK (`AutoCloseable` desde Java 21) creado dentro del método del bean: pasa a ser un bean propio que Spring cierra.
- Mejoras anotadas, no bloqueantes: el relay procesa un evento por ciclo; `PaymentTransactions` es una clase anónima dentro de la configuración.

## 4. Migraciones

Change de OpenSpec `expand-customer-name` (commits `73b096b` propuesta, `4ab2d06` implementación). El repo no tenía `full_name`; el caso equivalente es `customers.name VARCHAR(120) NOT NULL` (V4), que se separa en `first_name` / `last_name` con expandir-contraer.

| Pieza | Qué hace |
|---|---|
| `V11__expand_customer_name.sql` (expandir) | Prevalida: si hay nombres vacíos o solo con espacios, `RAISE EXCEPTION` con el recuento (sin datos personales) y Flyway + PostgreSQL revierten DDL y datos. Si no, añade las columnas anulables y rellena: espacios U+0020 recortados y colapsados, primera palabra / resto, `last_name` NULL con una sola palabra. `name` no se toca |
| `CustomerName` (dominio de ordering) | La misma regla en Java para la doble escritura del registro (la API sigue recibiendo un único nombre) |
| `db/checks/*.sql` | Cuatro invariantes, una consulta por fichero que devuelve los ids que la violan: `first_name` presente; `concat_ws` = `name` normalizado; componentes sin espacios en los extremos; `last_name` NULL ⇔ una palabra |
| `SchemaIT` (nivel 1) | Base aislada en el PostgreSQL común: todas las migraciones desde cero, ninguna pendiente ni fallida, entidades JPA validadas contra ese esquema |
| `CustomerNameMigrationIT` (nivel 2) | Base aislada hasta V10, casos límite (una palabra, varias, espacios en extremos y repetidos, tildes, ñ, guion, apóstrofo, tabulación, 120 caracteres), V11, resultados y checks a cero; cada check detecta su infracción; con un nombre en blanco la migración falla, el historial sigue en V10, nada cambia, y tras corregir la fila el reintento pasa |
| `CustomerNameRegistrationIT` | Un registro nuevo cumple los mismos checks (doble escritura) |
| CI `schema-compat` (nivel 3, cambio humano) | En cada PR: código de la rama base + migraciones de la rama, y sus tests de persistencia (`*RepositoryAdapterIT`, `*QueryAdapterIT`, `*PersistenceIT`, `CustomerRegistrationFacadeIT`) con Failsafe |

**Contraer queda para una versión posterior** (design.md del change): cuando no quede ningún escritor que solo rellene `name`, cambiar las lecturas a los componentes, repetir el backfill si los checks lo piden y, en otra versión, dejar de escribir y borrar `name`.

**Decisiones**: regla de partición "primera palabra / resto" (decisión de negocio; parte bien los dos apellidos habituales); ante datos históricos inválidos, fallar sin valores de sustitución; los tests de migración usan bases aisladas del servidor común, no contenedores propios (la propuesta inicial los pedía y se corrigió con `/opsx-update`).

**Hallazgo**: con la línea de Verify de `AGENTS.md` ya aclarada (paso 3), OpenCode completó las 10 tareas en un solo encargo, sin paradas.

## 5. Contextos y tiempo

- **Contextos**: 1 de integración (`missCount = 1` en Failsafe, paso 2) más los contextos pequeños de Surefire (`@WebMvcTest`, `@SpringJUnitConfig`).
- **Paralelismo por clases** (`junit-platform.properties`: clases `concurrent`, métodos `same_thread`). `IntegrationTest` lleva `@ResourceLock("integration-stores")` (heredado): su `@BeforeEach` vacía los cuatro almacenes, así que dos clases de integración no pueden correr a la vez.
- **`@Tag("slow")`** en `SessionVersionLatencyIT` (medición estadística de latencia, ~100 s) y en un test de `ProductCacheMultiInstanceIT` (TTL de 30 s): en local `-DexcludedGroups=slow`; `--full` y CI lo ejecutan todo.
- **Reutilización** en `~/.testcontainers.properties` (`testcontainers.reuse.enable=true`); en CI, desactivada.

| Medida | Resultado |
|---|---|
| Surefire en serie / en paralelo por clases | 21,2 s / **13,7 s** (436 tests, ninguno tocado) |
| IT en paralelo **sin** `@ResourceLock` | 492 tests: 75 fallos y 260 errores (deadlocks de PostgreSQL en el `TRUNCATE` de `cleanStores`) en **1 min 46 s** — `docs/evidencias/s16-paso5-paralelo-sin-lock.txt` |
| IT con `@ResourceLock` | 492 tests en verde, en serie |
| `verify` sin Surefire, 1.ª ejecución (arranca contenedores) | 4 min 11 s |
| `verify` sin Surefire, 2.ª ejecución (reutiliza) | 4 min 03 s |
| `verify` sin Surefire, reutilización y `-DexcludedGroups=slow` | **2 min 09 s** (490 IT en verde) |

**Lectura**: la reutilización solo ahorra el arranque de los contenedores (~8 s), porque desde el paso 2 se arrancan una vez por ejecución; con los 62 de la línea base habría ahorrado minutos. El mayor coste local era una sola clase de medición de latencia. El paralelismo de los IT daría otros ~2 min, pero exige abandonar la limpieza global y aislar los datos de cada test (ids únicos o una base por hilo): rediseño fuera del lab, anotado con la cifra que lo justifica.

## 6. CI

En `.github/workflows/acdc.yml` (zona protegida, cambio humano), job `quality`:

- `ubuntu-latest` (Docker incluido) y cache de Maven: ya estaban.
- **Descarga previa de las imágenes**: un paso propio antes de `verify-quality.sh --full`; la lista se extrae de `TestcontainersConfiguration` con `grep`, sin una segunda lista que se desactualice.
- **Reutilización desactivada**: `TESTCONTAINERS_REUSE_ENABLE=false`.
- **Salida por clase y logs de contenedores**: `MAVEN_ARGS=-Dmaven.test.redirectTestOutputToFile=true`; cada contenedor tiene un `Slf4jLogConsumer` (`container.postgres`, `container.mongo`, `container.redis`, `container.wiremock`), en `WARN` en local y `INFO` en CI (`LOGGING_LEVEL_CONTAINER`).
- **Artefacto si falla**: `surefire-reports/` y `failsafe-reports/` (con los `*-output.txt`), 7 días.
- **`schema-compat`** (paso 4): tests de persistencia de la rama base contra las migraciones del PR.

**`*Test` / `*IT`**: no se parte `quality` en dos jobs. El gate de cobertura de ramas de identity necesita la ejecución de Surefire y la de Failsafe juntas (parte de esa cobertura viene ahora de `*IT`), y separarlos obligaría a fusionar `jacoco.exec` en un tercer job. La separación ya la dan los nombres (ArchUnit) y `verify-quality.sh`: `--fast` = Surefire + los IT del gate de identity y `SchemaIT`; `--full` y CI = todo.

## 7. Protecciones del loop

| Capa | Qué se añadió |
|---|---|
| Guide | `AGENTS.md`, sección "Tests de integración y migraciones": `IntegrationTest` sin configuración propia, nada de `@DirtiesContext`, `*IT`/`*Test`, sin H2, fronteras con WireMock, fixtures tras la limpieza (nunca `@Sql`), expandir-contraer y migraciones inmutables, migraciones de datos con test e invariantes, SQL en text block, Docker ausente = problema de entorno |
| Verify (tests) | `verify-quality.sh`: `SchemaIT` en `--fast`; **umbral de contextos** (máximo 1 en Failsafe, leído del `missCount` de la cache de contextos en `failsafe-reports/*-output.txt`); migraciones publicadas en `main` sin modificar, renombrar ni borrar |
| Verify (ArchUnit) | `IntegrationTestRulesTest` (solo clases de test): solo `IntegrationTest` declara `@SpringBootTest`; las subclases concretas terminan en `IT`; nada depende de `org.h2`; ninguna subclase declara `@MockitoBean`, `@TestPropertySource`, `@DirtiesContext` ni `@Testcontainers` |
| Verify (zona protegida) | `scripts/protected-tests.txt`: `TestcontainersConfiguration`, `IntegrationTest`, `IntegrationTestInfrastructureIT`, `SchemaIT`. Hooks de Claude Code y plugin de OpenCode: una migración existente no se edita; la shell no escribe en `db/migration` |
| Guide dinámico | `guide-context.sh` (SessionStart de Claude Code y `session.created` del plugin de OpenCode): si `docker info` no responde en 10 s, aviso de entorno |

**Coste aceptado**: con `IntegrationTest` protegida, una tabla nueva obliga a que una persona la añada al `TRUNCATE` de la lista blanca (el agente debe parar y pedirlo).

## 8. Resultado

| Medida | Inicial | Final |
|---|---|---|
| Tests de integración (Failsafe) | 294 | **492** (los `*Test` que levantaban contexto pasan a `*IT`, más los nuevos de los pasos 3 y 4) |
| Contextos de Spring en los IT | 23 | **1** (verificado por `verify-quality.sh`; Surefire mantiene 7 contextos pequeños de `@WebMvcTest`/`@SpringJUnitConfig`) |
| Contenedores arrancados por ejecución | 62 | **4** (+ Ryuk) |
| Tiempo de la suite en CI (job `quality` en `main`) | 7 min 53 s (merge del S15) | **5 min 14 s** (merge del S16, PR #27), con más tests que antes |
| Tiempo local (`verify` sin Surefire) | 4 min 11 s sin reutilización | **2 min 09 s** con reutilización y `-DexcludedGroups=slow` |
| Clases con H2 / `@MockitoBean` fuera de `@WebMvcTest` / `@DynamicPropertySource` propio | 0 / 1 / 0 | **0 / 0 / 0** |

En el PR #27 pasaron los 8 checks a la primera; `schema-compat` ejecutó 33 tests de persistencia del código de `main` contra V10 y V11.
