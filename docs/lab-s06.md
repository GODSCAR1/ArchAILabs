# Lab S06 — Spotless y Checkstyle con gate en pre-commit

Repositorio: `master-acdc` (Spring Boot 4.1.1, Java 25), rama `lab/s06-calidad`. Agentes: Claude Code y OpenCode.
Versiones fijadas: Spotless 3.10.4 · google-java-format **1.36.1** · maven-checkstyle-plugin 3.6.0 · Checkstyle **14.3.0** · lefthook 2.2.2.

## Decisiones de partida

| Decisión | Motivo |
|---|---|
| Tras cada edición **solo Checkstyle** del archivo; Spotless al final del turno (`Stop` / `session.idle`) y en el pre-commit | Lab S03, prueba 3b: `spotless:apply` con `removeUnusedImports` entre dos ediciones borró un import aún sin usar → error de compilación. El ruleset no tiene reglas de formato, así que el veredicto de Checkstyle no depende de si el archivo está formateado. |
| Regla propia: nombres de test `should_…_when_…` | Error real: el agente nombró 12 tests sin `_when_` en el lab S05 (`session-ses_eb41.md`, parche que crea `OrderCancellationIT`). Además estaba en `AGENTS.md` como "pendiente: Checkstyle en S06". |
| `noFieldInjection` suprimida en `src/test` (por ruta, justificada) | En tests de Spring (`@SpringBootTest`, `@WebMvcTest`) JUnit crea la clase: `@Autowired MockMvc mockMvc;` es el patrón estándar. |
| Pre-commit con `spotless:apply` + `stage_fixed` (no `check`) | Lo pide el lab. Contradicción del curso: la lección 1 de la sección pone `check` en el pre-commit. Ambas respetan "lo revisado = lo mergeado" (el pre-commit reescribe antes de que exista el commit); lo que nunca cambia: CI solo `check`. |

## Paso 1 — Reformateo aislado

- Configuración de Spotless de la lección 2 (GOOGLE, `reflowLongStrings`, `formatAnnotations`, `sortPom`, `check` en `validate`) **antes** de reformatear, para que no haga falta un segundo reformateo masivo.
- **GJF 1.37.0 (última release) falla** con Spotless 3.10.4: `InvocationTargetException` sin causa visible. Ejecutado suelto con JDK 25 funciona → la incompatibilidad es plugin ↔ formateador. En el código de `spotless-lib` 4.10.4: GJF por defecto **1.36.1**, mínimo **1.30.0 en JDK 25** (`import module`, JEP 511). Fijado 1.36.1.
- **Entorno no honesto (Windows):** `spotless:check` marcó **90 archivos**; el diff solo cambiaba `\r\n` → `\n`. `git ls-files --eol`: 89 `.java` con `i/lf w/crlf` (índice LF, copia de trabajo CRLF, escritos antes de la regla `*.java eol=lf`). En CI (Linux) nunca habría fallado.
- Resultado: `git diff --stat` del reformateo = **2 archivos** (`pom.xml` por `sortPom` + config, y `OrderTransactionIT.java` por `reflowLongStrings`: dos strings SQL > 100 columnas partidos en `"…" + "…"`). Los ~88 archivos solo-CRLF desaparecen al hacer `git add`.
- Tests tras el reformateo: 77 unitarios, 0 fallos.
- Commits `b80a5b7` (style) y `6a85f86` (`.git-blame-ignore-revs`) + `git config blame.ignoreRevsFile`.
- Comprobación: `git blame -L 66,67 OrderTransactionIT.java` atribuye las líneas reescritas a `33a5095` (lab S05), no al reformateo.
- `.git-blame-ignore-revs text eol=lf` añadido a `.gitattributes` (aviso LF→CRLF de Git).

## Paso 2 — Ruleset propio

`config/checkstyle.xml`: base de la lección 3 + reglas propias, **todas en `error` y con mensaje accionable** (también las de la base, vía `<message key=…>`).

| Regla | Tipo | Motivo (también en `AGENTS.md`, sección "Estilo") |
|---|---|---|
| `noFieldInjection` | MatchXpath `VARIABLE_DEF` con `@Autowired` | Inyección por constructor; solo `src/main` |
| `noSystemOut` | RegexpSinglelineJava, `ignoreComments` | Logging con SLF4J |
| `noPrintStackTrace` | RegexpSinglelineJava | Suele acompañar a una excepción tragada |
| `noDisabledTests` | RegexpSinglelineJava | Cierra el hueco de `IllegalImport`: `@org.junit.jupiter.api.Disabled` con nombre completo y `@DisabledIf…`/`@DisabledOnOs`… |
| `testMethodName` (**la mía**) | MatchXpath sobre métodos `@Test`/`@ParameterizedTest`/`@RepeatedTest` con `matches()` | 12 tests del agente sin `_when_` (lab S05) |

Probado con Checkstyle 14.3.0 suelto sobre un archivo con violaciones: 17/17 esperadas con su mensaje; `@Autowired` en un campo de test no salta (supresión); `System.out` en un comentario no salta.
**Limitación conocida:** `@org.junit.jupiter.params.ParameterizedTest` con nombre completo no lo ve `testMethodName` (el nodo es una cadena `DOT`, no `IDENT`).

Maven: plugin 3.6.0 + dependencia explícita `checkstyle` 14.3.0, `validate`, `includeTestSourceDirectory`, `failOnViolation`, `violationSeverity=warning` (red de seguridad).

`./mvnw checkstyle:check` sobre el repo: **13 violaciones**, corregidas, no suprimidas:
- `MissingSwitchDefault` en `CreateOrderService`: **no era falso positivo**. `switch` de sentencia sobre `ReservationOutcome`: con un valor nuevo del enum, el pedido se guardaba **sin reserva** y en silencio. → expresión `switch` (`return switch … case RESERVED -> save(order)`): con un valor nuevo **no compila**. Mejor que un `default`, que también se lo tragaría.
- 12 `testMethodName` (11 en `OrderCancellationIT`, 1 en `InventoryFacadeIT`) renombrados a partir de su `@DisplayName`. `spec-trace` usa `@DisplayName` → la traza no se rompe.

Tras corregir: `checkstyle:check` ✅ · `spotless:check` ✅ · `spec-trace` ✅ · `verify`: 77 unitarios + 65 IT, 0 fallos (`failsafe-summary.xml`). Commit `a015456`.

## Paso 3 — Gate en pre-commit (lefthook)

`format` (`spotless:apply` + `stage_fixed`) y `style` (`checkstyle:check`, `fail_text`) en paralelo; se conserva `specs`.

| Prueba | Resultado |
|---|---|
| 3 — indentación rota a mano en `CreateOrderService` + commit | `format` reformateó y volvió a añadir el archivo; el commit (`5874433`) **solo contiene `lefthook.yml`**. 7,5 s en total. |
| 3b — `Debug.java` con `System.out` + commit | `style` **bloquea** (exit 1): `Debug.java:[5] … noSystemOut: Usa un Logger SLF4J…`. `HEAD` no cambia. |

Compromisos anotados: (1) el paralelo es seguro solo porque Checkstyle no tiene reglas de formato; (2) `stage_fixed` con `git add -p` vuelve a añadir el archivo entero; (3) Checkstyle analiza **todo** el proyecto en el pre-commit (6,7 s de los 7,5 s).

## Paso 4 — Inner loop y CI

| Pieza | Qué hace |
|---|---|
| `.claude/hooks/verify-file.sh` (PostToolUse `Edit\|MultiEdit\|Write`) | Checkstyle **solo del archivo** (`-Dcheckstyle.includes=<ruta bajo src/*/java>`), exit 2 + violaciones en stderr. Raíz derivada del archivo (worktrees), rutas Windows con `cygpath`. **Falla cerrado** (sin `jq`, JSON inválido o Checkstyle que no arranca → exit 2). |
| `.claude/hooks/verify-gate.sh` (Stop) | `spotless:apply` sobre el estado completo y luego `verify-quality.sh --fast`. Raíz desde el `cwd` del JSON. |
| `.opencode/plugins/acdc-style.ts` | `before` guarda la ruta por `callID`; `after` ejecuta Checkstyle del archivo y añade las violaciones a **`output.output`**. Spotless ya corría en `session.idle` (`verify-quality.ts`). |
| `verify-quality.sh` | Capa 1: `spotless:check` + `checkstyle:check`. |
| `.github/workflows/acdc.yml` | Job **`style`** (`spotless:check checkstyle:check`, solo check). |

Pruebas del hook fuera del agente (JSON por stdin), **6/6**:

| Entrada | Esperado | Resultado |
|---|---|---|
| main con `@Autowired` en campo | 2 | 2 (mensaje + `[6,3]`), 7 s |
| test `contextLoads()` | 2 | 2 (`testMethodName`, `[7,8]`) → `checkstyle.includes` también aplica a `src/test`, 7 s |
| main limpio | 0 | 0, 6 s |
| `pom.xml` | 0 | 0, 0 s |
| ruta estilo Windows (`C:\…`, JSON con `jq --arg`) | 2 | 2 |
| JSON inválido | 2 | 2 (**primero salió 0**: el hook fallaba abierto; corregido) |

Coste: ~7 s por edición, casi todo arranque de Maven (Checkstyle sobre un archivo tarda milisegundos).

## Paso 5 — Demostración

### Merge de la capa 1 y required checks
PR #5 (`lab/s06-calidad` → `main`) mergeado con `specs` ✅ (9 s), `style` ✅ (22 s) y `quality` ✅ (1 min 47 s) sobre `2147a5f`.
Ruleset de `main` (API `rules/branches/main`): `required_status_checks` = `specs`, `quality`, **`style`** · `pull_request` obligatorio · sin borrado ni force push.

### Prueba 4 — `--no-verify` y CI bloquea el merge ✅
Rama `lab/s06-prueba-4` desde `main`: `Debug.java` con `System.out` + `git commit --no-verify` (el pre-commit no corre) → PR #6.

| Check | Resultado | Motivo (log) |
|---|---|---|
| `style` | ❌ 17 s | `Debug.java:[5] (regexp) RegexpSinglelineJavaCheck#noSystemOut: Usa un Logger SLF4J…` |
| `quality` | ❌ 32 s | Mismo error en `▶ Capa 1: formato y estilo` de `verify-quality.sh --full`: cae antes de los tests |
| `specs` | ✅ 10 s | — |

Los tres son *Required*; "Merge pull request" desactivado (`mergeable_state=unstable`). PR cerrado sin mergear.
Dato de paso: en CI `spotless:check` dice "130 were already clean, 0 needs changes" → confirma que los 90 archivos del paso 1 eran solo CRLF local.
Capturas: `docs/evidencias/s06-p4-pr-bloqueado.png`, `s06-p4-style-log.png`, `s06-p4-quality-log.png`.
Pendiente menor (otro PR): `actions/checkout@v4`, `setup-java@v4`, `setup-node@v4` usan Node 20 (obsoleto) → subir a `@v5`.

### Pruebas 1, 2 y 5 — agentes
<!-- pendiente: Claude Code y OpenCode -->
