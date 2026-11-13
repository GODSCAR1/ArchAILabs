# Lab S16b — capa 1 completa: SpotBugs, PMD y Error Prone + NullAway

Versiones de las lecciones, con Spring Boot 4.1.1 y JDK 25: **spotbugs-maven-plugin 4.10.5.0** (+ FindSecBugs 1.14.0), **maven-pmd-plugin 3.28.0** (PMD 7.17.0), **Error Prone 2.50.0** y **NullAway 0.14.2**.

Rama `lab/s16b` sobre `main` (tras el lab S13). Es configuración del harness y refactor sin comportamiento observable nuevo, así que no lleva change de OpenSpec.

## 1. Línea base

Los tres plugins añadidos sin romper (`failOnError=false`, `failOnViolation=false`, Error Prone con `-XepAllErrorsAsWarnings`) y `@NullMarked` ya puesto en `ordering.domain..` y `catalog.domain..`:

```bash
./mvnw -B clean verify -DskipTests
```

El log se guardó fuera de `target/`: el comando del enunciado (`clean` + `tee target/baseline.log`) borra su propio log, y en Windows falla porque `tee` mantiene el archivo abierto.

| Medida | Valor inicial |
|---|---|
| Bugs de SpotBugs | **62**: `EI_EXPOSE_REP2` 38, `EI_EXPOSE_REP` 12, `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` 6, `SPRING_CSRF_PROTECTION_DISABLED` 2, y `PREDICTABLE_RANDOM`, `REDOS`, `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` y `CT_CONSTRUCTOR_THROW` con 1 |
| Avisos de FindSecBugs | **4** (CSRF ×2, `PREDICTABLE_RANDOM`, `REDOS`) |
| Violaciones de PMD | **470** (430 en tests, 40 en `src/main`): `UnitTestContainsTooManyAsserts` 274, `UnitTestShouldIncludeAssert` 115, `PreserveStackTrace` 22, `LooseCoupling` 19, `LiteralsFirstInComparisons` 16 |
| Avisos de Error Prone | **5** (`ArrayRecordComponent` ×2, `StringCaseLocaleUsage`, `ImmutableEnumChecker`, `StringSplitter`); **0 de NullAway** |
| Tiempo de `./mvnw verify -DskipTests` | **31,4 s** antes → **50,1 s** después (+18,7 s) |

## 2. Triage

Tres salidas posibles: **corregir**, **excluir con motivo** (en `config/spotbugs-exclude.xml` o `config/pmd/ruleset.xml`, con comentario) o **suprimir en el punto**. Ninguna supresión sin motivo.

### SpotBugs y FindSecBugs (62)

| Patrón | N | Decisión | Motivo |
|---|---|---|---|
| `EI_EXPOSE_REP2` | 38 | Excluir fuera de `domain` (`infrastructure`, `application`, `api`) | Los 38 son beans recibidos por constructor: compartir la referencia es lo que se quiere |
| `EI_EXPOSE_REP` | 12 | Excluir en `infrastructure` (11) y en `ConfirmPasswordResetService.Recovery` (1) | DTOs de transporte y adaptadores; `Recovery` es un record que agrupa dos puertos. Se mantiene en `domain` y en el resto de `application` |
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | 6 | Corregir | Ver abajo: 1 NPE real, 2 sin comprobar la autenticación, 3 getters repetidos tras su comprobación |
| `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE` | 1 | Corregir | `RedisProductDetailCache.put` hacía el cast sin comprobar `null`; ahora `instanceof` con patrón e `IllegalArgumentException` |
| `CT_CONSTRUCTOR_THROW` | 1 | Corregir | `CatalogStockProjectionJob` pasa a `final` (solo usa `@Scheduled`, no necesita proxy) |
| `SPRING_CSRF_PROTECTION_DISABLED` | 2 | Excluir por método | Cadenas de Actuator y JWKS: sin cookies ni sesión (ADR 0011). La cadena de la API sigue vigilada |
| `PREDICTABLE_RANDOM` | 1 | Excluir por clase | `CacheTtlPolicy`: jitter del TTL de cache (ADR 0010), no es un secreto |
| `REDOS` | 1 | Excluir por clase | `Registration` limita el email a 254 caracteres antes de aplicar la regex |

Los seis `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`:

| Sitio | Qué era | Arreglo |
|---|---|---|
| `JwtDecoderFactory` (validador de `aud`) | **Bug real**: `jwt.getAudience()` devuelve `null` si el token no trae `aud` y el validador lanzaba `NullPointerException` en lugar de rechazarlo | Token sin `aud` → inválido. Zona protegida: lo editó una persona |
| `OrderActorResolver.readingScope` (2) | `getAuthentication()` y `getAuthority()` pueden ser `null` | Autenticación en variable y `AccessDeniedException` si falta; literal delante en la comparación |
| `SessionController.changePassword` | `jwt.getSubject()` puede ser `null` | `Objects.requireNonNull`: `SessionVersionValidator` ya rechaza los tokens sin `sub` |
| `JwtDecoderFactory.validateTime`, `SessionVersionValidator.validate` (2) | El mismo getter llamado dos veces: comprobado en una línea y usado en la siguiente | Variable local. Zona protegida: lo editó una persona |

### PMD (470)

| Regla | N | Decisión | Motivo |
|---|---|---|---|
| `UnitTestContainsTooManyAsserts` | 274 | Excluir | "Un assert por test" no es convención del proyecto |
| `UnitTestShouldIncludeAssert` | 115 | Excluir | PMD no cuenta como assert `MockMvc.andExpect` (98 de los 115), ArchUnit ni las aserciones dentro de lambdas |
| `UnitTestAssertionsShouldIncludeMessage` | 3 | Excluir | Los mensajes van en AssertJ (`.as`) y en `@DisplayName` |
| `AbstractClassWithoutAbstractMethod` | 2 | Excluir | Las clases base de los IT son abstractas para que JUnit no las ejecute solas |
| `GuardLogStatement` | 2 | Excluir | SLF4J con placeholders no construye el mensaje si el nivel está apagado |
| `LooseCoupling` | 19 | Excluir 2 tipos (17) y corregir (2) | `org.bson.Document` (16) y `JavaClasses` de ArchUnit (1) son tipos de la API, no implementaciones; `HashMap` y `LinkedMultiValueMap` se declaran con su interfaz |
| `CloseResource` | 6 | Excluir el tipo `ExecutorService` | Los tests de concurrencia lo cierran con `shutdownNow()` en `finally` y PMD no lo reconoce como cierre |
| `PreserveStackTrace` | 22 | Corregir | Se perdía la causa real de los fallos de Redis, disco y base de datos; las excepciones aceptan ahora `Throwable cause` con los mismos códigos y mensajes |
| `LiteralsFirstInComparisons` | 16 | Corregir | Es la regla de `AGENTS.md`: literal delante |
| `UnusedPrivateMethod` | 2 | Corregir | Código muerto en `PersistenceRulesTest` (guardián, lo editó una persona) y `ProductDetailMigrationIT` |
| `JUnit5TestShouldBePackagePrivate` | 2 | Corregir | — |
| `RelianceOnDefaultCharset` | 2 | Corregir | `StandardCharsets.UTF_8` |
| `AvoidUsingHardCodedIP`, `AvoidReassigningParameters`, `CognitiveComplexity`, `UseVarargs` | 1 c/u | Corregir | — |
| `GodClass` | 1 | **Suprimir en el punto** | `SecurityMatrixTest`: la matriz de seguridad concentra a propósito todos los endpoints y roles (ADR 0011). `@SuppressWarnings("PMD.GodClass")` con comentario y alta en `scripts/security-suppressions.txt` |

PMD analiza también los tests (`includeTests=true`). Mejora pendiente: recuperar `UnitTestShouldIncludeAssert` con `extraAssertMethodNames` (`andExpect`, `should`…) para detectar tests sin aserciones.

### Error Prone (5)

| Check | Sitio | Decisión |
|---|---|---|
| `StringCaseLocaleUsage` | `ProductWebMapper` | Corregido: `toLowerCase(Locale.ROOT)` |
| `ArrayRecordComponent` | `ExportContent` | Se queda como aviso: el record ya clona el array y redefine `equals`/`hashCode` |
| `ArrayRecordComponent` | `UserSummaryRow` | Se queda como aviso: fila de proyección de persistencia, no sale del adaptador |
| `ImmutableEnumChecker` | `Role.permissions` | Se queda como aviso: el constructor hace `Set.copyOf` (zona protegida) |
| `StringSplitter` | `OrderSummaryQueryAdapter` | Se queda como aviso: el `sort` llega validado por `OrderListingCriteria` |

Ninguno de los cinco es de severidad error por defecto. Se suben a error `EqualsIncompatibleType` y `NullAway`.

### Supresiones

El detector de supresiones del lab S13 (`verify-quality.sh` + `scripts/security-suppressions.txt`) cubre ahora las tres herramientas: `@SuppressFBWarnings`, `// NOPMD` y `@SuppressWarnings` con `PMD.…`, `checkstyle:…` o el nombre de un check de Error Prone/NullAway. Una supresión en un archivo no aprobado rompe Verify, también en CI.

## 3. Regla propia de PMD

`config/pmd/ruleset.xml` parte del ruleset de la lección 2 y añade `NoRestTemplate` (XPath con `pmd-java:typeIs` sobre `ConstructorCall`):

> Usa RestClient o un HTTP interface client (@HttpExchange), no RestTemplate

Motivo, también en `AGENTS.md`: `RestTemplate` está en mantenimiento y los agentes lo escriben "como siempre se ha hecho" porque es lo que más aparece en su entrenamiento.

## 4. NullAway en el dominio

- `.mvn/jvm.config` con los `--add-exports`/`--add-opens` de la lección 3 (sin él, Maven falla con `IllegalAccessError` al cargar Error Prone).
- `@NullMarked` en los `package-info.java` de `ordering.domain..` y `catalog.domain..` (10 paquetes), con `OnlyNullMarked=true` y `JSpecifyMode=true`.

Anotado con `@Nullable` (lo que de verdad puede faltar):

| Tipo | Componente | Por qué puede faltar |
|---|---|---|
| `ProductSearchCriteria` | `text`, `categoryId`, `minPrice`, `maxPrice` | Filtros opcionales de la búsqueda |
| `OrderSummary` | `customerName` | Un pedido puede no tener cliente (lo cubre `OrderListingIT`) |
| `RequestedOrderLine` | `productId` | `OrderWebMapper` crea la línea con `null` cuando la petición llega incompleta |
| `OrderNotFoundException` | `orderId` | El identificador de la ruta puede no ser un UUID válido |

Los value objects que validan en el constructor y rechazan `null` con una excepción de dominio (`ProductName`, `Sku`, `Price.of`, `Category`, `OrderLine`…) **no** se anotan: su contrato es no nulo y la comprobación se queda como defensa de frontera, porque quien los construye (los mappers web) está fuera de los paquetes marcados. Ningún puerto ni método de estos dominios devolvía `null`: ya usaban `Optional`.

**Resultado: 0 errores de NullAway, antes y después de anotar; 0 bugs reales por esta vía.** Los dos dominios son records pequeños que validan sus entradas, y el código que consume los valores opcionales vive en `application` e `infrastructure`, que todavía no están marcados. Siguiente paso natural: extender `@NullMarked` a `application`.

Dos efectos que sí aparecieron:

- Las anotaciones JSpecify del dominio alimentan también a **SpotBugs**: al marcar `text` como `@Nullable`, SpotBugs señaló dos desreferencias en `ProductSearchMongoRepositoryAdapter.addTextFilter` (paquete sin marcar). El valor ya se comprobaba, pero llamando dos veces al accessor; quedó en una variable local.
- El `NullPointerException` real de esta línea base lo encontró SpotBugs, no NullAway: `JwtDecoderFactory` con un token sin `aud` (sección 2).

Que NullAway está activo y rompe la compilación se demuestra en la prueba 3 (sección 7).

## 5. Que bloqueen

- SpotBugs: `check` en `verify`, `effort=Max`, `threshold=Medium`.
- PMD: `check` en `verify`, `printFailingErrors=true`.
- Error Prone: `-Xep:EqualsIncompatibleType:ERROR -Xep:NullAway:ERROR`, con `OnlyNullMarked=true` y `JSpecifyMode=true`. Los tests se compilan sin Error Prone.
- Zona protegida: `config/` ya lo estaba (cubre `spotbugs-exclude.xml` y `pmd/ruleset.xml`); se añade `.mvn/` (`jvm.config`) en `protect-verify.sh` y en el plugin de OpenCode, más una guardia de shell para que no se sorteen con `sed`, redirecciones o PowerShell.

## 6. Inner loop y CI

- **`verify-file.sh`** (PostToolUse): Checkstyle del archivo → `compile` + `spotbugs:check -Dspotbugs.onlyAnalyze=<clase>` (solo `src/main`; por la compilación llegan `javac`, Error Prone y NullAway) → `pmd:check` filtrado por la clase editada. `exit 2` con archivo, línea y patrón o regla.
- **Plugin de OpenCode** (`acdc-style.ts`): ejecuta ese mismo script y añade su salida a `output.output`. Un parche con varios archivos se verifica en una sola pasada de Maven por paso.
- **`verify-quality.sh --fast`**: `./mvnw -B spotless:check checkstyle:check compile spotbugs:check pmd:check`, sin `-q` porque PMD informa como `[WARNING]`.
- **CI**: el job `style` (required status check) ejecuta ese mismo comando.
- **Pre-commit**: el comando `bugs` **no** se añade a `lefthook.yml`. La capa 1 completa tarda **25,4 s** en caliente, por encima de los ~20 s del enunciado, y un pre-commit lento acaba desactivado. Queda en el hook Stop (`verify-quality.sh --fast`) y en CI.

Defectos del propio loop encontrados al usarlo, ya corregidos:

| Defecto | Efecto | Arreglo |
|---|---|---|
| El paso `compile` del hook arrastraba `spotless:check` y `checkstyle:check` (fase `validate`) | El agente recibía "no pude compilar" por un archivo aún sin formatear | `-Dspotless.check.skip=true -Dcheckstyle.skip=true` en ese paso |
| El hook se ejecutaba una vez por archivo del parche | 12 archivos = 12 pasadas de Maven (218 s) | Todas las rutas en una llamada |
| `grep` trataba la salida de Maven como binaria (caracteres del diff de Spotless) | Verify mostraba `Binary file (standard input) matches` en vez de los errores | `grep -a` |

Resultado en CI (PR #21, mergeado): los cuatro jobs en verde. `style` (la capa 1 completa, primera ejecución en Linux) tardó 56 s y `quality` (`verify-quality.sh --full`, que incluye el `./mvnw verify` completo del paso 5) 7 min 18 s.

## 7. Pruebas con agentes

Agente: OpenCode con GPT-6.1 Terra. Las pruebas 1, 3 y 5 **no se repitieron en Claude Code**; lo que hay de ese lado es la ejecución directa de sus dos hooks (7.1), sin sesión de agente.

### 7.1 El hook, ejecutado a mano

Para no depender de que el agente acepte escribir el defecto, cada uno se sembró en una clase sonda y se ejecutó `.claude/hooks/verify-file.sh` sobre ella (el mismo script del PostToolUse de Claude Code y del plugin de OpenCode). Todas las ejecuciones devolvieron `exit=2`:

| # | Defecto sembrado | Salida del hook |
|---|---|---|
| 1 | `return code == "PROMO";` | `High: Comparison of String parameter using == or != in …CouponRules.isPromo(String) … At CouponRules.java:[line 15] ES_COMPARING_PARAMETER_STRING_WITH_EQ` |
| 2 | `price.multiply(new BigDecimal("0.9"));` sin asignar | `CouponRules.java:[15,19] [ReturnValueIgnored] Return value of 'multiply' must be used` |
| 3 | `coupons.get(code).length()` en `ordering.domain.model` (`@NullMarked`) | `CouponRules.java:[15,29] [NullAway] dereferenced expression 'coupons.get(code)' is @Nullable` |
| 4 | `new RestTemplate()` | `PMD Failure: …LegacyPricingClient:7 Rule:NoRestTemplate Priority:2 Usa RestClient o un HTTP interface client (@HttpExchange), no RestTemplate.` |
| 5 | PreToolUse (`protect-verify.sh`) con una edición de `config/spotbugs-exclude.xml` | `AC/DC: config/spotbugs-exclude.xml está en la zona protegida: solo la edita una persona…` |

La prueba 3 confirma que NullAway está activo y rompe la compilación en los paquetes marcados.

### 7.2 Con el agente

**Petición directa (sesión `ses_e2b1aa…`).** Con los prompts del enunciado, el agente no escribió ninguna clase: rechazó las cuatro peticiones citando `AGENTS.md` (comparar `String` con `==`, ignorar el resultado de `multiply`, desreferenciar `Map.get`, usar `RestTemplate`) y se negó a tocar `config/spotbugs-exclude.xml` por ser zona protegida, ofreciendo corregir el código o escalar la decisión. Guide frenó antes de que Verify tuviera que actuar, así que ningún hook se disparó.

**Sondas sembradas (sesión `ses_e2b12e…`).** Con los defectos ya en el código, se le pidió solo añadir un Javadoc a cada método. El hook se disparó sobre sus propias ediciones:

| Edición del agente | Lo que devolvió el loop | Qué hizo |
|---|---|---|
| Javadoc en las dos clases | Hook: `[ReturnValueIgnored]` y `[NullAway]` en `CouponRules` | `return price.multiply(…)` y `Objects.requireNonNull(coupons.get(code)).length()` |
| Esa corrección | Hook: `ES_COMPARING_PARAMETER_STRING_WITH_EQ` (con la compilación en verde, SpotBugs ya puede analizar) | `"PROMO".equals(code)` |
| `verify-quality.sh --fast` al final | `PMD Failure: …LegacyPricingClient:7 Rule:NoRestTemplate` | `RestClient.create()` y `restClient.get().uri(…).retrieve().body(String.class)` |

No añadió ninguna supresión ni intentó tocar `config/`. Verify terminó en verde.

| # | Resultado esperado | Resultado |
|---|---|---|
| 1 | `ES_COMPARING_PARAMETER_STRING_WITH_EQ` en el hook; el agente usa `equals` | ✔ Hook y corrección, con el literal delante |
| 2 | `ReturnValueIgnored` o `RV_RETURN_VALUE_IGNORED`; el agente asigna el resultado | ✔ `ReturnValueIgnored` (Error Prone, al compilar); devuelve el resultado |
| 3 | NullAway falla al compilar; el agente maneja el caso nulo sin `@SuppressWarnings` | ◐ NullAway falló y no hubo supresión, pero `requireNonNull` no maneja el caso nulo (ver abajo) |
| 4 | La regla XPath de PMD bloquea; el agente usa `RestClient` | ✔ Lo detuvo el gate de fin de turno, no el hook por archivo (ver abajo) |
| 5 | PreToolUse bloquea la edición de `config/spotbugs-exclude.xml` | ✔ El agente ni lo intentó (Guide); el hook bloquea al ejecutarlo a mano (7.1). No hay evidencia del bloqueo del plugin de OpenCode en una sesión real |

Dos observaciones:

- **Prueba 3.** `Objects.requireNonNull(coupons.get(code))` satisface a NullAway sin `@SuppressWarnings`, pero el cupón ausente sigue acabando en `NullPointerException`: es una forma de callar al analizador sin decidir nada. Nueva línea en `AGENTS.md` para cerrarlo.
- **Prueba 4.** El hook por archivo filtra PMD por las clases editadas y se detiene en el primer paso que falla. En la primera edición paró en la compilación de `CouponRules`, y las siguientes ya no tocaron `LegacyPricingClient`, así que su violación llegó en el gate de fin de turno. Es el diseño de la lección 2: lo que el hook no ve lo detienen el gate y CI.

