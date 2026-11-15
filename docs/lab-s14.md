# Lab S14 — tests del módulo de seguridad: niveles, contextos y cobertura de ramas

Rama `lab/s14` sobre `main` (tras el lab S16b). Spring Boot 4.1.1, JDK 25, **JaCoCo 0.8.15** (soporte oficial de Java 25 desde la 0.8.14; la 0.8.15, de junio de 2026, es la última). Es harness y tests; el único cambio de comportamiento es el arreglo de `iat` (paso 5), así que no lleva change de OpenSpec.

## Adaptación del enunciado al repo

| Enunciado | `master-acdc` | Decisión |
|---|---|---|
| Paquetes `dev.codeja.shop.security*` / `auth*` | Un solo módulo: `com.codeja.master_acdc.identity` | Alcance = `identity` + tests `*Security*`/`*Auth*` + `OrderActorResolverTest` |
| `./mvnw test -Dtest='*Security*,*Auth*,*Token*'` | Los `*IT` corren en Failsafe y `test` no los ejecuta | `verify` con `-Dtest` y `-Dit.test`, sin Checkstyle/SpotBugs/PMD (se mide la suite, no la capa 1) |
| `JwtIssuerTest`, `RefreshTokenServiceTest`, `OrderAuthorization.canRead` | `JwtTokenAdapterTest`, `RefreshServiceTest`, `OrderActorResolver.readingScope` | Se usan los nombres del repo |
| `IntegrationTest` | `IdentityIntegrationSupport` (ya era la base de casi todos) | Se conserva el nombre |
| Claves efímeras en una `@TestConfiguration` común | Ya existía: `GlobalIdentityTestExtension` genera la clave RSA en cada ejecución | Sin cambios; nadie mockeaba el `JwtEncoder` |
| `SecurityMatrixTest` como `@WebMvcTest` | Guardián; el 404 de objeto ajeno depende de datos reales en la BD | Sigue `@SpringBootTest`, pero en el contexto común |
| `RefreshTokenRepositoryTest` con `@DataJpaTest` para el `FOR UPDATE` | `RefreshTokenLockTimeoutIT` y `RefreshTokenConcurrencyIT` ya lo prueban contra Postgres real | No se añade: sería un contexto más (objetivo 2) para lo que ya está probado |
| Umbral para `dev.codeja.shop.security*`/`auth*` a 0,85 | `identity*` | 0,80 (paso 7) |

Comando de medida (pasos 1 y 8), con el log fuera de `target/` porque `clean` lo borraría:

```bash
( time ./mvnw -B clean verify \
    -Dtest='com/codeja/master_acdc/identity/**/*Test,*Security*Test,*Auth*Test,OrderActorResolverTest' \
    -Dit.test='com/codeja/master_acdc/identity/**/*IT,*Security*IT,*Auth*IT' \
    -Dcheckstyle.skip -Dspotbugs.skip -Dpmd.skip \
    -Dlogging.level.org.springframework.test.context.cache=DEBUG ) > ../s14-baseline.log 2>&1
```

Los contextos se cuentan con el `missCount` de las estadísticas de la cache de contextos (`DefaultContextCache`) en cada JVM (Surefire y Failsafe), y la clase que provoca cada miss es la última `Running …` anterior.

## 1. Línea base

| Medida | Valor inicial |
|---|---|
| Número de tests | **383** (270 Surefire + 113 Failsafe) |
| Tiempo total | **4 min 31 s** de Maven con `clean` (Surefire 64,9 s, Failsafe 170,0 s) |
| Contextos de Spring creados | **11** (7 + 4) |
| Clases con `@MockitoBean`/`@MockitoSpyBean` | **6** (`MethodAuthorizationTest` con 8 spies, `AdminUserManagementIT`, `SessionVersionRevocationIT`, `AuthFlowIT` con el `Clock`, `AuthSessionVersionScenarios`, `PasswordResetControllerTest`) |
| Aserciones con `assertTrue`/`assertEquals` | **0**: la suite ya usaba AssertJ (los 7 `assertSame` de la matriz son un método propio de `SecurityMatrixSupport`; queda un `assertAll` en `PasswordResetFlowIT`) |

Origen de cada contexto:

| JVM | Clase que lo creó | Por qué |
|---|---|---|
| Surefire | `AdminUsersQueryCountTest` | `@TestPropertySource` (statement inspector propio) |
| Surefire | `ApiSecurityTest` | `catalog.cache.enabled=true` propio |
| Surefire | `PasswordResetControllerTest` | `@WebMvcTest` (slice: legítimo) |
| Surefire | `MethodAuthorizationTest` | 8 `@MockitoSpyBean` |
| Surefire | `SecurityMatrixTest` | `@Import` de exports + propiedades |
| Surefire | `SessionVersionQueryCountTest` | `@TestPropertySource` (otro inspector) |
| Surefire | `OrderAuthorizationQueryCountTest` | `generate_statistics=true` |
| Failsafe | `ActuatorSecurityIT` | la base |
| Failsafe | `AdminUserManagementIT` | spy de `RedisSessionVersions` (lo reutiliza `SessionVersionRevocationIT`) |
| Failsafe | `AuthFlowIT` | spy del `Clock` y de `RedisSessionVersions` |
| Failsafe | `SessionVersionLatencyIT` | `catalog.cache.enabled=false` |

`RefreshTokenLockTimeoutIT` falló en la línea base con 1016 ms frente al mínimo de 1500 ms: es el intermitente abierto en `docs/lab-s12.md` (en CI y aislado tarda ~2,1 s; hipótesis: reloj entre Windows y el Postgres de Docker Desktop). No se toca en este lab; en las demás ejecuciones pasó.

## 2. Cobertura inicial

`jacoco-maven-plugin` con `prepare-agent` + `report` (fase `verify`). `prepare-agent` rellena `argLine`, que Surefire y Failsafe usan por defecto: unitarios e IT escriben en el mismo `target/jacoco.exec` y el informe, que va después de Failsafe, los junta. No hay `argLine` a mano en el pom (si algún día lo hay, debe incluir `@{argLine}`). Se excluye el metamodelo JPA generado (`**/*_.class`).

| Paquete (`identity.…`) | Líneas | Ramas |
|---|---|---|
| `infrastructure.adapters.out.security` | 94,9 % | 76,8 % (63/82) |
| `domain.model` | 87,5 % | 69,7 % (46/66) |
| `application` | 99,5 % | 88,2 % (30/34) |
| `infrastructure.adapters.in.web` | 95,9 % | 87,0 % (20/23) |
| `infrastructure.adapters.out.redis` | 96,4 % | 83,3 % (5/6) |
| `infrastructure.adapters.out.ordering` | 93,3 % | 50,0 % (2/4) |
| `infrastructure.config` | 95,2 % | **18,8 %** (3/16) |
| `infrastructure.adapters.out.persistence` | 100 % | 100 % (2/2) |
| **Total `identity`** | **95,4 %** | **73,4 %** (171/233) |
| `OrderActorResolver` | 96,2 % | 90,0 % (9/10) |

Es la pista del enunciado: 95 % de líneas con 73 % de ramas, porque los tests recorren el camino feliz.

Ramas sin cubrir más relevantes:
- **Reglas**:
  - Caducidad absoluta de la familia en `RefreshService`.
  - `exp` ausente, `iat`/`nbf` futuros en `JwtDecoderFactory.validateTime`.
  - Cambio de contraseña y reset con la cuenta deshabilitada.
  - Fail closed de `SessionRevocationTransactions` (0/10) y `LockedSessionVersions`.
  - Actor anónimo en `CurrentOrderActorAdapter`.
  - Carácter `\0` en contraseñas.
  - Unicidad que no es la del email en `TransactionalRegister`.
  - Invariantes de los `record`.
  - `toString()` "redacted" que nunca se ejecutaban.
- **Defensivas**:
  - `catch (NoSuchAlgorithmException)`: SHA-256 está garantizado en toda JVM.
  - `IOException` de `CommonPasswordsAdapter` (recurso del classpath).
  - `body == null` en `PasswordResetWebMapper` (Spring rechaza antes un cuerpo ausente).
  - `authentication == null` en `OrderActorResolver.readingScope`: `account()` ya lo garantiza.
  - `publish` distinto de "OK" en `RedisSessionVersions`.

## 3. Reorganización por niveles

| Qué | Nivel | Clase |
|---|---|---|
| Mapeo rol → permisos | Unitario | `RoleTest`, `RolesAndPermissionsConverterTest` |
| Lectura por propietario/soporte | Unitario | `OrderActorResolverTest` |
| `SessionVersionValidator` | Unitario | `SessionVersionValidatorTest` |
| Emisión y validación de tokens | Unitario con `FixedClockExtension` y clave RSA generada | `JwtTokenAdapterTest` |
| Rotación y reutilización | Unitario del servicio | `RefreshServiceTest` (con `RefreshTokenAssert`) |
| `FOR UPDATE` y timeout del lock | Integración en el contexto común | `RefreshTokenLockTimeoutIT`, `RefreshTokenConcurrencyIT` |
| Reglas por URL, 401/403/404 en `ProblemDetail` | `@SpringBootTest` en el contexto común (guardián) | `SecurityMatrixTest` |
| Controlador aislado | `@WebMvcTest` | `PasswordResetControllerTest` |
| Flujo completo | `@SpringBootTest` + Testcontainers | `AuthFlowIT` |

Cambios (la persona preparó la base y los guardianes; OpenCode migró el resto):
- **`IdentityIntegrationSupport`, base única.** Lleva todas las propiedades que antes estaban sueltas (cache del catálogo activa, estadísticas de Hibernate, statement inspector), el `@Import` de `IdentityTestContainers` e `IdentityTestBeans` (directorio de exports temporal) y **12 spies comunes**: `spiedClock`, `spiedSessionVersions`, los 8 de `MethodAuthorizationTest` y 2 que el agente subió desde `OrderRefundIT`. Un spy envuelve el bean real, y Mockito lo resetea tras cada test.
- **Las subclases ya no añaden configuración.** Esto incluye las de `ordering` y `exports` que heredan de la base: `AdminExportDownloadIT` dejó su propio `@Primary Path`.
- **`SqlCapture` compartida.** Solo graba entre `start()` y `stop()`, y la base la cierra tras cada test, así que no acumula el SQL de toda la suite.
- **`FixedClockExtension`** (la de la lección 1) en `JwtTokenAdapterTest` y en los unitarios nuevos.
- **`AGENTS.md`, sección "Niveles de test"**: nivel más bajo posible; los `@SpringBootTest` no añaden configuración y usan los spies de la base; nunca se mockea el emisor ni el verificador de tokens; `Clock` inyectado; aserciones de comportamiento y `RefreshTokenAssert`.

Resultado intermedio: **3 contextos** (la base en cada JVM, porque Surefire y Failsafe son procesos distintos, y el slice `@WebMvcTest`), 383 tests y 3 min 45 s.

## 4. Aserciones

La suite ya estaba en AssertJ. Se añadió la aserción de dominio `RefreshTokenAssert`, con `isRevoked()`, `isUsed()` y `belongsToFamily(UUID)`. Sigue las cuatro piezas de la lección: `extends AbstractAssert`, `static assertThat`, cada método empieza con `isNotNull()` y falla con `failWithMessage` incluyendo el id del token, y `return this`.

## 5. Huecos cerrados

Tests nuevos, todos en el nivel unitario:

| Regla | Test | Qué verifica |
|---|---|---|
| Validación temporal del JWT | `JwtTokenAdapterTest` | `exp` ausente, `exp == ahora`, `iat` futuro y `nbf` futuro → inválido; `nbf` pasado → válido |
| **JWT sin `iat`** | `JwtTokenAdapterTest` | sin `iat` → `JwtValidationException`; el mismo token con `iat` → válido |
| Cuenta deshabilitada | `ChangePasswordServiceTest`, `RequestPasswordResetServiceTest` | cambio rechazado y sin escrituras; reset con la misma respuesta (`Optional.empty`) para cuenta inexistente y deshabilitada, sin token ni notificación |
| Fail closed | `SessionRevocationTransactionsTest`, `LockedSessionVersionsTest` | 08xxx, 40003 y 57P01 → `session-version-unavailable` con la causa; otro SQLState → se propaga (`isSameAs`); BD caída al reconstruir → unavailable; cuenta inexistente → `session-account-absent` |
| Actor anónimo | `CurrentOrderActorAdapterTest` | sin autenticación o no autenticada → `Optional.empty` y sin consultar la cuenta |
| `\0` en contraseñas | `PasswordPolicyTest`, `PasswordEncoderAdapterTest` | rechazada |
| Unicidad ajena al email | `TransactionalRegisterTest` | otra constraint u otro SQLState → la excepción original |
| Invariantes | `IdentityRecordInvariantsTest` | casos inválidos de 7 `record`, incluido `Lifetime` con `expiresAt` posterior a `familyExpiresAt` |
| Sin secretos en logs | `IdentityRedactionTest` | 13 objetos con secretos: `toString()` no contiene ni el secreto ni el email |

Las 6 reglas mínimas del enunciado:

| Regla | Dónde |
|---|---|
| Token caducado, revocado o ya usado en el refresh | `RefreshServiceTest`, `AuthFlowIT` |
| Usuario deshabilitado al refrescar | `RefreshServiceTest` |
| Email inexistente y contraseña incorrecta con la misma respuesta | `LoginServiceTest`, `AuthFlowIT` |
| `SessionVersionValidator` con versión distinta y con Redis caído (fail closed) | `SessionVersionValidatorTest`, `SessionVersionRedisIT` |
| Lectura de pedidos de dueño, no dueño y soporte | `OrderActorResolverTest`, `OrderDetailIT`, matriz |
| Rol desconocido en el JWT, sin 500 | `RolesAndPermissionsConverterTest` |

Dos hallazgos del agente, que se detuvo en ambos para preguntar:
- **Código muerto, borrado.** En `RefreshService.rotate` había una comprobación de la caducidad absoluta de la familia que nunca podía cumplirse. El constructor de `RefreshToken.Lifetime` exige `expiresAt <= familyExpiresAt`, así que si la familia ha caducado el token también, y lo rechaza la condición anterior. El tope de la familia sigue protegido por el invariante de `Lifetime` (test nuevo) y por `should_cap_expiry_when_family_has_one_day_left`.
- **Regla rota, arreglada.** El `if` de `JwtDecoderFactory.validateTime` rechaza `issuedAt == null`, pero esa condición no se cumplía nunca: si falta `iat`, el `MappedJwtClaimSetConverter` de Spring lo rellena con `exp - 1 s` antes del validador. Un JWT sin `iat` firmado con nuestra clave pasaba. La persona cambió el conversor en la zona protegida: ahora conserva el `iat` del token o deja el claim vacío. El test nuevo falla si se deshace el arreglo. Es el caso que describe el enunciado: la línea estaba cubierta y la regla no se cumplía, y solo la cobertura de ramas lo mostró.

Cobertura tras el paso 5, con el comando de medida:

| Paquete (`identity.…`) | Líneas | Ramas |
|---|---|---|
| `application` | 100 % | 100 % (32/32) |
| `domain.model` | 97,4 % | 83,3 % (55/66) |
| `infrastructure.adapters.in.web` | 98,8 % | 87,0 % (20/23) |
| `infrastructure.adapters.out.ordering` | 100 % | 100 % (4/4) |
| `infrastructure.adapters.out.persistence` | 100 % | 100 % (2/2) |
| `infrastructure.adapters.out.redis` | 96,4 % | 83,3 % (5/6) |
| `infrastructure.adapters.out.security` | 95,8 % | 85,7 % (72/84) |
| `infrastructure.config` | 100 % | 81,3 % (13/16) |
| **Total `identity`** | **98,7 %** | **87,1 %** (203/233) |

Lo que queda sin cubrir:
- Validación de configuración al arrancar (`JwtSettings`, `PemKeyLoader`).
- Condiciones compuestas de los `record`, con algún operando no ejercido.
- Las ramas defensivas del paso 2.

## 6. El test que demuestra que el test sirve

Mutación manual con `../s14-mutaciones.sh`. Por cada regla cambia una línea, ejecuta los tests que deberían protegerla, guarda el informe y restaura el archivo con `git restore`, también si se interrumpe con Ctrl+C.

| Regla | Mutación | Tests que fallan | Mensaje |
|---|---|---|---|
| Reutilización de refresh token | Comentar `tokens.revokeFamily(...)` en `RefreshService.rotate` | `RefreshServiceTest`, 2 de `AuthFlowIT`, 1 de `SessionLifecycleIT` | *reutilizar un refresh token ya rotado debe revocar TODA la familia (ADR 0011)* |
| Objeto ajeno → 404 | `findVisibleById` sin `visibleTo(scope)` | `OrderDetailIT`, `SecurityMatrixTest` (caso 37) | *[pedido ajeno, inexistente o mal formado (…): 404 sin revelar si existe] expected: 404 but was: 200* |
| Claims exactos | Añadir `.claim("email", …)` al access token | 4 de `JwtTokenAdapterTest`, 4 de `AuthFlowIT` | *[claims del access token (ADR 0011): exactamente estos ocho, ningún dato personal]* … `"email"="a@example.com"` … *to contain only following keys* |

En la primera pasada fallaron las tres, pero con mensajes que no nombraban la regla: `Wanted but not invoked`, `expected:<404> but was:<200>`. Se añadió una descripción al `verify` de Mockito, y en los otros dos tests un status comprobado con AssertJ y `.as(...)` en las aserciones de claims. Esto se hizo en el test principal de cada regla; la matriz (guardián) y `AuthFlowIT` siguen como segunda línea con su mensaje genérico.

## 7. Umbral en el loop

**80 % de ramas en cada paquete de `identity`** (`rule` con `element=PACKAGE`, `include=com.codeja.master_acdc.identity*`).

Por qué 0,80:
- Tras el paso 5 el total es 87 %, y los paquetes más bajos son `config` (81,3 %) y `domain.model` (83,3 %).
- Lo que les falta es configuración de arranque, operandos de condiciones compuestas y ramas defensivas imposibles.
- Con 0,85 habría que escribir tests sin valor para pasar.
- Con 0,80 queda algo más de un punto de margen y se siguen cazando regresiones reales. Por ejemplo, quitar los tests de fail closed baja `config` por debajo.

Cómo está montado:
- **`pom.xml`**: ejecución `check` después de `report`, con `minimum=${acdc.coverage.branch.minimum}` (0.80) y `haltOnFailure=${acdc.coverage.gate}` (false por defecto). Una ejecución parcial (`-Dtest=…`), como las del agente o la del script de mutaciones, mide solo una parte de la suite y no debe fallar por eso.
- **`verify-quality.sh`**: hace vinculante el gate (`-Dacdc.coverage.gate=true`) en `--fast` y en `--full`, y antes borra `target/jacoco.exec`. Sin eso, el agente acumula datos de ejecuciones anteriores y la cobertura sale inflada. Si falla, el agente recibe el motivo y qué hacer: cubrir ramas de regla, no bajar el umbral, y que un test sin aserciones no cuenta.
- **`AGENTS.md`**: la regla, con su motivo.
- **Zona protegida**: `pom.xml` y `verify-quality.sh` ya lo eran (Sección 2).

**Primera ejecución de `--fast`: falló el gate.** `adapters.out.redis` quedó en 66,7 % (4/6). La rama de la carrera de reconstrucción (`SETNX` perdido en `RedisSessionVersions.populate`) solo la prueba `SessionVersionRedisIT`, con Redis real, y el loop rápido solo ejecutaba `AuthFlowIT`. Se añadió `SessionVersionRedisIT` al loop rápido. Desde el paso 3 comparte contexto con `AuthFlowIT`, así que solo suma el tiempo de sus tests. Con eso `redis` queda en 83 % y `--fast` pasa.

## 8. Medida final

_Pendiente._

## 9. Pruebas con agentes

_Pendiente._
