# AGENTS.md

Las reglas marcadas "(pendiente: … Sxx)" aún NO tienen verificador automático: cúmplelas igual y no las describas como verificadas.

## Comandos (tu shell en Windows es PowerShell: invoca los scripts con `bash`)
- Compilar y probar: `bash ./mvnw -q test`. Formato: `bash ./mvnw spotless:apply`.
- Verify: `bash ./verify-quality.sh --fast` (specs + capa 1 + tests; tarda varios minutos: ejecútalo con un timeout de al menos 600 s). Solo estilo: `bash ./mvnw -q checkstyle:check`.
- Capa 1 completa (formato, estilo, compilación con Error Prone/NullAway, SpotBugs y PMD): `bash ./mvnw -B spotless:check checkstyle:check compile spotbugs:check pmd:check`. Sin `-q`: PMD informa como `[WARNING] PMD Failure`.

## Arquitectura (hexagonal estricta)
- `domain/` NUNCA importa Spring, JPA ni Jackson (`@Entity`, `@Repository`, `@Service`, `@Component` prohibidos ahí). (pendiente: ArchUnit en S17)
- `application/` depende solo de `domain/`; NUNCA de `infrastructure/`. (pendiente: ArchUnit en S17)
- Puertos de salida en `domain/port/` (solo interfaces); implementaciones en `infrastructure/adapters/`.
- Casos de uso: interfaz en `application/port/` (`<Accion>UseCase`) + implementación `<Accion>Service` en `application/`. Adaptadores en `infrastructure/adapters/in/<tecnología>` y `infrastructure/adapters/out/<tecnología>`.
- Los beans se ensamblan en `infrastructure/config/` con `@Bean`. Sin lógica de negocio en métodos `@Bean`.
- NUNCA `ApplicationContext.getBean()` ni campos estáticos para acceder a beans fuera de `infrastructure/config/`. (pendiente: ArchUnit en S17)
- La conversión entre capas vive en mappers dedicados de infraestructura: `adapters/in/<tecnología>/<Agregado><Tecnología>Mapper` (petición → criterio/comando de aplicación; resultado → DTO de respuesta), `adapters/out/persistence/<Agregado>PersistenceMapper` (entidad JPA ↔ modelo de dominio o fila de lectura) y `adapters/out/mongo/<Agregado>MongoMapper` (documento ↔ modelo de dominio). Clases `final`, sin estado, con métodos estáticos y test unitario. Motivo: el código generado reparte la conversión entre entidades, DTO y adaptadores; un único sitio por frontera la hace revisable y testeable. (pendiente: ArchUnit en S17)
- Las entidades JPA y los DTO web NO tienen métodos de conversión (`from`, `toDomain`, `toRow`…), ni los controllers ni los adaptadores contienen la lógica de mapeo. `domain` y `application` nunca conocen los mappers. (pendiente: ArchUnit en S17)
- Sin librerías de mapeo (MapStruct y similares): una dependencia nueva es decisión de una persona. (pendiente: ArchUnit en S17)
- Lee el `package-info.java` de un paquete antes de añadir clases en él.

## Código y tests
- Spring Boot 4 / Java 25: en tests usa `@MockitoBean` (no `@MockBean`) y `spring-boot-starter-webmvc-test` para `@WebMvcTest`.
- Value objects como `record` inmutables que validan en el constructor.
- Nombres de test: `should_<comportamiento>_when_<condición>` (ver "Estilo").
- Todo método público de un caso de uso en `application/` tiene test unitario.
- Integración con Testcontainers; NUNCA mocks de base de datos para verificar queries.

## Estilo (Checkstyle: `config/checkstyle.xml`, todas las reglas rompen el build)
El formato NO se discute ni se hace a mano: Spotless lo aplica al final de tu turno y en el pre-commit. Checkstyle comprueba el estilo tras cada edición y te devuelve archivo, línea y qué hacer.
- Inyección por constructor (campo `private final` + constructor); NUNCA `@Autowired` en campos de `src/main`. Motivo: dependencias explícitas, clases testeables sin Spring. (En tests de Spring sí se permite.)
- Logging con SLF4J; NUNCA `System.out`, `System.err` ni `printStackTrace()`. Motivo: la consola no tiene nivel, contexto ni correlación, y `printStackTrace` suele acompañar a una excepción tragada.
- NUNCA captures `Exception`, `RuntimeException` ni `Throwable`, ni dejes un `catch` vacío: captura la excepción concreta y tradúcela a una de dominio, o deja que se propague al manejador global. Motivo: el atajo típico "para que compile" esconde fallos.
- NUNCA desactives un test (`@Disabled` con o sin import, `@DisabledIf…`, `@DisabledOnOs`…). Motivo: poner el build en verde desactivando un test es bajar el umbral.
- Tests: `should_<comportamiento>_when_<condición>` en snake_case minúscula; la frase legible va en `@DisplayName` (que además traza el escenario de la spec). Motivo: el agente nombró 12 tests sin `_when_` en el lab S05.
- Métodos ≤ 40 líneas, ≤ 5 parámetros, complejidad ciclomática ≤ 10, archivos ≤ 500 líneas: extrae métodos con nombre o agrupa parámetros en un record. Motivo: el código generado tiende a resolverlo todo en un método largo con `if` anidados.
- `switch` sobre enums: usa una expresión `switch` (`case X -> ...`) para que el compilador exija cubrir todos los valores. `equals` siempre con `hashCode`. Sin imports con `*`.
- NUNCA añadas `@SuppressWarnings("checkstyle:...")` ni edites `config/checkstyle*.xml`: si una regla no aplica, detente y explica por qué; una persona decide la supresión y la justifica.

### Bugs y malas prácticas (SpotBugs + FindSecBugs, PMD, Error Prone + NullAway — lab S16b, `docs/lab-s16b.md`)
Tras cada edición de un `.java` se compila y se analiza la clase: recibes archivo, línea y el patrón o la regla. Corrige el código; el aviso no se discute ni se silencia.
- Compara `String` con `equals()`, NUNCA con `==` (compara referencias). Literal delante: `"PROMO".equals(code)`. Lo detecta SpotBugs (`ES_COMPARING_*`).
- `BigDecimal`, `String`, `Instant` y los `record` son inmutables: asigna o devuelve SIEMPRE el resultado de `multiply`/`add`/`subtract`/`setScale`/`trim`/`replace`/`plus`. Lo detectan Error Prone (`ReturnValueIgnored`) y SpotBugs (`RV_RETURN_VALUE_IGNORED`).
- NUNCA captures `NullPointerException`: elimina la causa (comprueba el valor o usa `Optional`). Motivo: el `catch` esconde el bug y cualquier otro NPE del bloque. Lo detecta PMD (`AvoidCatchingNPE`).
- Clientes HTTP: usa `RestClient` o un HTTP interface client (`@HttpExchange`); NUNCA `RestTemplate`. Motivo: `RestTemplate` está en mantenimiento y los agentes lo escriben "como siempre se ha hecho" porque es lo que más aparece en su entrenamiento. Regla propia de PMD: `NoRestTemplate`.
- Paquetes `@NullMarked` (hoy: `ordering.domain..` y `catalog.domain..`): todo es no nulo por defecto. Lo que puede faltar se devuelve como `Optional` (retornos) o se anota con `@Nullable` de `org.jspecify.annotations` (campos y parámetros). NUNCA devuelvas `null` ni desreferencies el resultado de `Map.get` o de un método `@Nullable` sin decidir antes qué pasa cuando falta (comprobación, valor por defecto, excepción de dominio, `Optional`). Lo detecta NullAway al compilar.
- `Objects.requireNonNull(x)` NO es manejar el caso nulo: sigue siendo un `NullPointerException`. Úsalo solo cuando otra parte del sistema garantiza el valor, y di cuál en el mensaje. Sobre el resultado de `Map.get` o de una búsqueda, decide qué pasa cuando falta. Motivo: en el lab S16b el agente calló a NullAway con `requireNonNull(coupons.get(code))`.
- Getters de colecciones en `domain`: devuelve una copia inmutable (`List.copyOf`), nunca la colección interna (`EI_EXPOSE_REP`).
- NUNCA silencies un aviso: prohibido `@SuppressFBWarnings`, `@SuppressWarnings("PMD.…")`, `@SuppressWarnings("NullAway")` o con el nombre de un check de Error Prone, y `// NOPMD`. Tampoco edites `config/spotbugs-exclude.xml`, `config/pmd/ruleset.xml`, `.mvn/jvm.config` ni los `compilerArgs` de `pom.xml`. Si crees que un aviso no aplica, detente y explica por qué: la exclusión la decide y la justifica una persona. `verify-quality.sh` falla si aparece una supresión en un archivo no aprobado.
- Un `IllegalAccessError` al compilar es un problema del build (`.mvn/jvm.config`), no de tu código: avisa a la persona en lugar de cambiar código.

## Persistencia (JPA: inventory y ordering) — ADR `docs/decisiones/listado-de-pedidos.md`
- Todas las asociaciones son `fetch = FetchType.LAZY`; en `@ManyToOne`/`@OneToOne` escríbelo explícito (por defecto son EAGER). NUNCA `FetchType.EAGER`, `hibernate.enable_lazy_load_no_trans` ni `spring.jpa.open-in-view=true`. (Checkstyle `toOneLazy`/`noEagerFetch`)
- Qué se carga lo decide la consulta del caso de uso: listado → proyección (`select new …Row(...)`); detalle → `@EntityGraph` con una sola colección; volumen grande → batch fetching + paginación.
- Si aparece una `LazyInitializationException`, carga la asociación que nombra el mensaje en la consulta del caso de uso. NUNCA la resuelvas con EAGER, OSIV, `enable_lazy_load_no_trans`, `Hibernate.initialize()` en bucle ni consultas dentro de un bucle.
- NUNCA llames a un repositorio dentro de un bucle (`findById` por elemento): carga todos los ids de una vez (`findAllById`) o usa una proyección. El batch fetching NO agrupa esas llamadas.
- Los servicios devuelven DTOs o records de dominio, nunca entidades JPA.
- Todo endpoint de listado tiene un test de número de consultas en el fast loop (`*Test`): datos con MÁS filas que `default_batch_fetch_size` (p. ej. 120), `flush()` + `clear()`, `Statistics.clear()` y un umbral constante (el coste del código correcto, p. ej. `≤ 2`). El número de consultas no puede depender del número de filas.
- En proyecciones con `left join`, cuenta una columna no nula de la entidad unida (`count(l.productId)`), nunca la entidad o el embeddable (`count(l)`): las filas vacías del `left join` cuentan 1.
- Consultas paginadas con `join fetch`: `countQuery` explícito y test de `getTotalElements()`.
- `default_batch_fetch_size=50` es la red de seguridad del proyecto: no lo cambies (zona protegida).

## Consultas dinámicas y concurrencia
- En los módulos JPA (inventory, ordering), los filtros dinámicos usan `Specification` sobre el metamodelo (`Entidad_`) combinadas con
  `allOf`; NUNCA JPQL ni SQL concatenado. Motivo: los criterios tipados evitan nombres de
  columnas manipulables y mantienen la consulta parametrizada. (pendiente: Semgrep en S12)
- El `Sort` solo acepta una lista blanca de campos y SIEMPRE añade `id` como último criterio.
  Motivo: evita ordenar por campos arbitrarios y hace estable la paginación. (verificador:
  `ProductControllerTest` para la lista blanca y `ProductSearchPaginationMongoTest` para el `id` final)
- Las transacciones se abren con `TransactionTemplate` en `infrastructure/config`; NUNCA
  `@Transactional` en `domain` ni `application`. Motivo: mantiene la frontera hexagonal y evita
  la auto-invocación: un método privado anotado no abre una transacción. Si se pide
  `@Transactional` en un método privado del mismo servicio, explica la auto-invocación y no lo
  hagas. (verificador: `PersistenceRulesTest`)
- Las ediciones del catálogo usan concurrencia optimista con el campo `version` del documento
  (actualización condicional por `_id` + `version`): NUNCA un `save` que sobrescriba sin
  comparar. Motivo: detecta ediciones concurrentes sin bloqueo. (verificador:
  `ProductEditingMongoRepositoryAdapterTest`)
- Todo método `findForUpdate*` lleva `@Lock(PESSIMISTIC_WRITE)` y el hint
  `jakarta.persistence.lock.timeout`; nunca se quita el bloqueo ni el timeout para "evitar
  fallos": el 409/503 es el contrato. Motivo: serializa la sección crítica y hace observable la
  contención en lugar de perder actualizaciones. (verificador: `PersistenceRulesTest` y test de
  timeout de bloqueo)
- Si un cambio rompe la compilación o la firma que usa un test guardián, DETENTE y pide a una
  persona que decida (actualizar el guardián o cambiar el diseño). NUNCA añadas constructores,
  sobrecargas o adaptadores "de compatibilidad" para esquivarlo. Motivo: en el lab S09 (prueba 1)
  un constructor `Object... legacyValues` con casts mantuvo los guardianes en verde y esquivó la
  regla de 5 parámetros sin que ninguna capa lo detectara. Si Checkstyle pide agrupar parámetros,
  agrúpalos en un record: es la deuda de diseño que el guardián hace visible. (verificador:
  `DomainSignatureRulesTest` para `Object`/`Object[]` en `domain`; el resto, revisión humana)
- Los tests de `scripts/protected-tests.txt` son guardianes: no se borran, no se renombran y no se
  debilitan. Motivo: protegen los contratos de concurrencia y persistencia del laboratorio.
  (verificador: `protect-verify.sh`, `verify-quality.sh` y el plugin de verificación)

## Catálogo en MongoDB — ADR `docs/adr/0009-catalogo-en-mongodb.md` y modelo `docs/catalog-model.md`
Lee los dos documentos antes de tocar el catálogo: el modelo sale de los patrones de acceso.
- El catálogo vive solo en MongoDB: NUNCA JPA, `jakarta.persistence` ni Spring Data JPA en `catalog`. Motivo: se retiró PostgreSQL del catálogo (V7). (verificador: `CatalogMongoArchitectureTest`)
- Arrays acotados: `latestReviews` ≤ 5 y `variants` ≤ 100; las reseñas completas viven en la colección `reviews` y la ficha guarda solo el subset y `rating`. NUNCA embebas un array sin límite (todas las reseñas, historiales…) aunque "la ficha sea más rápida": propón un subset con tope y detente. Motivo: un documento que crece sin límite llega al máximo de 16 MB y degrada cada lectura. (verificador: validador `$jsonSchema` y `CatalogMongoStructureIT`)
- Todo índice se declara en `CatalogIndexInitializer`; `spring.data.mongodb.auto-index-creation=false`. Cada filtro nuevo del buscador añade su índice ahí; `CatalogMongoStructureIT` (guardián) exige la lista exacta de índices, así que detente y pide a una persona que añada el nuevo nombre. Motivo: los índices son decisiones de diseño revisables, no efectos de anotaciones. (verificador: `CatalogMongoStructureIT` y `verify-quality.sh`)
- Consultas y agregaciones con la clase de entrada (`CatalogProduct.class`, `CatalogReview.class`) y proyecciones si solo necesitas campos; NUNCA el nombre de la colección como texto ni `Document.class`. Motivo: sin la clase, Spring no mapea `category.id` → `category._id` ni convierte tipos, y una agregación sobre el nombre devuelve un único grupo con `_id: null`. (verificador: `verify-quality.sh`)
- `CatalogSchemaValidator` (validador `$jsonSchema`) es zona protegida: NUNCA lo desactives, relajes ni sortees "porque da problemas"; si rechaza un documento, el documento está mal o el modelo debe cambiar con un ADR. (verificador: `protect-verify.sh`, plugin de verificación y `verify-quality.sh`)
- `availableStock` es una copia de `stock.available` (dueño: inventory) que refresca `CatalogStockProjectionJob`; NUNCA reserves ni valides stock contra el documento del catálogo.
- La ficha se resuelve en 1 operación y el buscador en 2 (página + count); el número de operaciones no depende de variantes, atributos ni reseñas. (verificador: `ProductDetailQueryCountMongoTest` y `CatalogSearchMongoQueryCountTest`)

## Cache — ADR `docs/adr/0010-cache-multinivel.md` y tabla `docs/caching.md`
Lee los dos antes de tocar una cache.
- Toda cache tiene su fila en `docs/caching.md` ANTES del código (clave, TTL L1/L2, estrategia, quién invalida). Sin fila no hay cache.
- Clave explícita (`key = "#..."`) y `sync = true`; NUNCA `unless` ni varias caches en la misma anotación. Motivo: sin `sync`, cada miss concurrente va a MongoDB (stampede). (verificador: tests de concurrencia de la cache y `verify-quality.sh`)
- Solo valores inmutables del dominio (`ProductDetail`); NUNCA documentos Mongo ni `@Entity`. (verificador: `CatalogMongoArchitectureTest`)
- TTL de L1 < 30 s con jitter incluido y jitter ±10 % en L1 y L2. Los TTL viven solo en `CacheTtlPolicy` (zona protegida): si un TTL debe cambiar, detente y pídelo. Motivo: el TTL de L1 es la red de seguridad cuando se pierde un mensaje de invalidación. (verificador: `CacheTtlPolicyTest`, `protect-verify.sh` y el plugin de verificación)
- El checkout (`ordering`, `inventory`) NUNCA lee de la cache ni de `ProductDetailUseCase`, y el stock NUNCA se cachea como dato para decidir. (verificador: `CatalogMongoArchitectureTest`)
- Invalida tras el commit (`ProductChanged` + `AFTER_COMMIT`); NUNCA antes del commit ni con `transactionAware()` solo en L2.
- Redis caído = miss; NUNCA un 5xx por la cache. Estas reglas son para caches de DATOS (catálogo). EXCEPCIÓN única: la versión de sesión de identity (`shop:identity:session-version:v1:`) es un control de seguridad, no una cache: sin L1 ni TTL, Redis caído = 401 (fail closed) y la barrera se publica ANTES del commit (lab S12, change add-session-version-revocation). No extiendas esta excepción a otras claves. Si cambia la forma de un valor cacheado, sube la versión del prefijo `shop:cache:vN:`.

## Seguridad
- NUNCA loguear Request/Response completos, datos personales (email, documento, teléfono, tarjeta) ni variables `token`, `accessToken`, `refreshToken` o `password`. (verificador: Semgrep `shop-no-log-tokens`)
- Secretos SOLO por variables de entorno (`${VARIABLE}`); NUNCA en código, tests ni archivos versionados. En tests, genera el valor o léelo de una variable de entorno. NUNCA leas `.env`: pide a la persona el nombre de la variable. (verificador: Gitleaks en el hook, el pre-commit y CI)
- NUNCA SQL ni JPQL concatenado (tampoco `String.format`); siempre parámetros enlazados o Criteria. La ordenación dinámica, solo con lista blanca de campos. (verificador: Semgrep `shop-sql-concatenation`, `shop-sql-injection-taint`, `shop-order-sort-injection`)
- Tokens, códigos de verificación, OTP, sales y nonces SOLO con `SecureRandom`; NUNCA `Random`, `ThreadLocalRandom` ni `Math.random`. (verificador: Semgrep `shop-no-weak-password-reset-random`)
- Rutas de fichero desde la entrada: base absoluta normalizada, `resolve(...).normalize()` y `startsWith(base)` antes de leer; mejor, identificadores en lugar de nombres. (verificador: Semgrep `shop-export-path-traversal`)

### Capa 2: SAST y secretos (lab S13, `docs/lab-s13.md`)
- Cada edición pasa por Semgrep (solo `semgrep-rules/`) y Gitleaks: si el hook informa de un hallazgo, corrige la causa en el código.
- PROHIBIDO añadir `nosemgrep` o `gitleaks:allow`, y editar `semgrep-rules/`, `.semgrepignore`, `.gitleaks.toml`, `gitleaks-baseline.json` o `.acdc/open-findings.md`. Si crees que un hallazgo es un falso positivo, explícalo para la descripción del PR y detente: la supresión la decide una persona.
- PROHIBIDO `git commit --no-verify`/`-n`, `LEFTHOOK=0` o `core.hooksPath` para saltar el pre-commit.

### Autenticación y autorización — ADR `docs/adr/0011-autenticacion-refresh-y-version-de-sesion.md`
Lee el ADR antes de tocar seguridad. `HttpSecurityConfiguration`, `JwtTokenAdapter`, `JwtDecoderFactory`, `SessionVersionValidator`, `Role` y la matriz (`SecurityMatrixTest`/`SecurityMatrixSupport`) son zona protegida: si crees que hay que cambiarlos, explica por qué y detente.
- La API es stateless: bearer JWT RS256 de 15 min; NUNCA sesión HTTP, `permitAll()` amplio ni `anyRequest().permitAll()`. Las rutas públicas se abren por método y ruta exacta. (verificador: `SecurityMatrixTest`)
- NUNCA desactives CSRF en `/api/auth/refresh` ni `/api/auth/logout`: usan cookie. Si el frontend da 403, le falta la cabecera `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`. (verificador: `AuthFlowIT`)
- Claims EXACTOS del access token: `iss, sub, aud, iat, exp, jti, roles, sv`. NUNCA añadas email, permisos, customerId ni nada "para ahorrar una consulta". (verificador: `AuthFlowIT`, claims exactos)
- Autorización por PERMISOS: `@PreAuthorize("hasAuthority('<permiso>')")` en un DECORADOR de `infrastructure` NO final (CGLIB) sobre el caso de uso; domain y application sin Spring. `hasRole('X')` busca `ROLE_X`. (verificador: `AuthorizationArchitectureTest`, `MethodAuthorizationTest`)
- Propiedad del objeto: el cliente sale del `sub` vía la cuenta y se filtra EN LA CONSULTA (también en el UPDATE); un objeto ajeno responde 404, nunca 403 ni 200. NUNCA un parámetro, cabecera o campo `customerId` en la API pública. `order:read-any` amplía solo lecturas. (verificador: `AuthorizationArchitectureTest`, `SecurityMatrixTest`)
- Todo endpoint nuevo añade sus filas a la matriz (anónimo y los cinco roles), incluida la de objeto ajeno; la matriz la cambia una persona. Errores 401/403 como ProblemDetail conservando `WWW-Authenticate`.
- Versión de sesión: fail closed (Redis caído = 401), sin cache local ni TTL; incrementar versión, revocar familias y publicar la barrera ANTES del commit, en la misma transacción. (verificador: `SessionVersionRevocationIT`)

| Rol | Permisos |
|---|---|
| CUSTOMER | order:create, order:read-own, order:cancel-own |
| CATALOG_MANAGER | product:write, stock:reserve |
| SUPPORT | order:read-any, order:refund |
| ADMIN | product:write, stock:reserve, order:read-any, order:refund, user:read, user:manage |

Spring Security 7 — APIs eliminadas (no las uses aunque aparezcan en ejemplos antiguos):

| Eliminado | Usa |
|---|---|
| `WebSecurityConfigurerAdapter` | beans `SecurityFilterChain` |
| `authorizeRequests()`, `antMatchers()`, `mvcMatchers()` | `authorizeHttpRequests()` + `requestMatchers()` |
| encadenar con `.and()` | DSL con lambdas (`csrf(c -> ...)`) |
| `AntPathRequestMatcher` / `MvcRequestMatcher` | `PathPatternRequestMatcher` |
| `@EnableGlobalMethodSecurity` | `@EnableMethodSecurity` |
| `NoOpPasswordEncoder`/`{noop}` en producción | `PasswordEncoderFactories.createDelegatingPasswordEncoder()` |

## Verify (no negociable)
- Antes de terminar cualquier tarea: `bash ./verify-quality.sh --fast` en verde y pega su salida completa.
- Si Verify falla: corrige la CAUSA indicada (archivo, línea, regla).
- PROHIBIDO para ponerlo en verde: desactivar reglas, bajar umbrales, `@Disabled`, borrar o debilitar tests, excluir archivos, `git commit --no-verify`.
- PROHIBIDO editar `config/`, `.mvn/`, `.claude/`, `.opencode/`, `.github/`, `pom.xml` (incluye dependencias nuevas), `.env*`, `verify-quality.sh`, `scripts/`, `semgrep-rules/`, `.semgrepignore`, `.gitleaks.toml`, `gitleaks-baseline.json`, `.acdc/open-findings.md`, `lefthook.yml`, `openspec/config.yaml`, `src/main/resources/application*`, `CatalogSchemaValidator.java`, `CacheTtlPolicy.java`, `ProductDetailCache.java`, `HttpSecurityConfiguration.java`, `JwtTokenAdapter.java`, `JwtDecoderFactory.java`, `SessionVersionValidator.java`, `Role.java`. Si hace falta, detente y pide permiso a una persona.

## graphify
- Para preguntas sobre el código, consulta primero el grafo (sus nodos están en inglés: pregunta en inglés): `graphify query "<pregunta>"`, `graphify path "<A>" "<B>"`, `graphify explain "<concepto>"`. Lee `graphify-out/GRAPH_REPORT.md` solo para una visión general.
- Las aristas `INFERRED`/`AMBIGUOUS` son hipótesis, y un nodo sin aristas no prueba que no se use (inyección por tipo, YAML): confírmalo en el código antes de borrar nada.
- Tras modificar código, o si no encuentras algo recién creado, ejecuta `graphify update .` (sin coste de modelo).

## Especificaciones (OpenSpec)
- Todo cambio con comportamiento observable nuevo empieza con `/opsx:propose` (OpenCode: `/opsx-propose`). No implementes hasta que una persona haya revisado la propuesta.
- `openspec/specs/` y `openspec/changes/archive/` están protegidos: el comportamiento cambia con un delta en `openspec/changes/<nombre>/`.
- Nunca archives ni sincronices specs (`openspec archive`, `/opsx:sync`): lo hace una persona tras revisar el PR.
