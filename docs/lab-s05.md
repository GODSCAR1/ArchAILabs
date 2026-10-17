# Lab S05 — De la spec al merge con OpenSpec

Repositorio: `master-acdc` (`com.codeja.master_acdc`, Spring Boot 4.1.1, Java 25). Agentes: Claude Code y OpenCode (`@opencode-ai/plugin` 1.15.4).
OpenSpec: **1.14.3** (`generatedBy` de los comandos instalados) — esta es la versión que se fija en CI.

## Decisión de partida: el dominio de pedidos no existía

El repo solo tenía el catálogo (`Product`, `Sku`, `Price`). El lab da por hecho `ordering` e `inventory`, y el repositorio de referencia del curso (zip S03 L05) tampoco los trae. Opciones: (A) un change previo `add-order-placement` con OpenSpec, (B) adaptar el lab al catálogo, (C) un `Order` mínimo a mano.

**Elegida: A.** El ciclo se recorre dos veces, la spec de `ordering` nace con `create` y la liberación de stock de la cancelación tiene sentido porque antes hubo una reserva.

## Paso 1 — Instalación y configuración

- `openspec init --tools claude,opencode --language es` ejecutado por mí (el agente no puede escribir en `.claude/` ni `.opencode/`). Límite de "solo planificar" comprobado en `.opencode/commands/opsx-propose.md` ("Planning boundary").
- `openspec/config.yaml` con el contexto real del proyecto (no el de la plantilla `dev.codeja.shop`) y reglas para los cuatro artefactos.
- `AGENTS.md`: sección "Especificaciones (OpenSpec)" (44 líneas en total).

Huecos del loop que corregí antes de empezar:

| Hueco | Corrección |
|---|---|
| El gate de fin de turno (`verify-gate.sh` y `session.idle`) salía si no había cambios en `src/` → nunca validaba specs durante `propose` | `git status --porcelain -- src openspec` |
| `scripts/`, `lefthook.yml` y `openspec/config.yaml` fuera de la zona protegida | Añadidos a `protect-verify.sh`, al plugin y a `AGENTS.md` |
| ArchUnit no estaba en `pom.xml` (zona protegida) | `archunit-junit5` 1.4.2 añadido por mí |
| `.gitattributes` solo forzaba LF en `mvnw` | `*.sh` y `*.ts` con `eol=lf` |

## Paso 3 — Verify de specs (adaptado a Windows)

Los scripts de la lección no funcionaban en este entorno. Versiones corregidas:

| Problema del script de la lección | Corrección |
|---|---|
| Claude Code pasa rutas `C:\...\openspec\specs\...`; el patrón `*/openspec/specs/*` nunca coincidía → el hook no bloqueaba nada | Normalización con `cygpath -u` y `\` → `/` |
| El agente de OpenCode usa PowerShell: `Set-Content`, `Out-File`, `Copy-Item`… no estaban en la lista heurística | Cmdlets añadidos al filtro de Bash |
| `verify-spec.sh` hacía `cd "$CLAUDE_PROJECT_DIR"` → valida el árbol equivocado en un worktree | Raíz derivada de la ruta del archivo editado |
| El plugin llamaba a `openspec` desde el shell de Bun (en Windows existe como `.cmd`) | `bash -c "openspec validate ..."` |
| Los hooks no fallaban cerrado | `command -v jq/openspec || exit 2` |

Pruebas fuera del agente (JSON por stdin), 7/7 con el código esperado:

| Entrada | Esperado | Resultado |
|---|---|---|
| Edit de spec viva (ruta Windows) | 2 | 2 |
| Write de un change | 0 | 0 |
| Bash `openspec archive x --yes` | 2 | 2 |
| Bash `openspec validate --all --strict` | 0 | 0 |
| Bash `sed -i` sobre spec viva | 2 | 2 |
| Bash `cat` de spec viva | 0 | 0 |
| PowerShell `Set-Content` sobre spec viva | 2 | 2 |

`verify-quality.sh --fast` en verde con la capa `▶ Specs`. Lefthook: `openspec validate --all --strict` en pre-commit.
**CI** (repo público `GODSCAR1/ArchAILabs`, rama `main`): `.github/workflows/acdc.yml` con dos jobs.
- `specs`: OpenSpec 1.14.3 fijado, `fetch-depth: 0`, `validate --all --strict`, `validate --archived`, `spec-trace.sh` y, en PR, `spec-guard.sh`.
- `quality`: Java 25 + `verify-quality.sh --full` (IT con Testcontainers en el runner). Incluye `chmod +x mvnw`: el bit de ejecución no viaja desde Windows.
- `spec-guard.sh` **endurecido por capacidad**: cada spec viva modificada exige un delta archivado de esa misma capacidad en el PR. Probado con 4 casos; el script de la lección dejaba pasar "edito `ordering` a mano y archivo un change de `inventory`".
- `CODEOWNERS` sobre `openspec/specs/` y `openspec/changes/archive/`. Con una sola cuenta, GitHub no permite aprobar los PR propios: la revisión obligatoria de code owners queda desactivada y la barrera es el required check `specs`.
- Primer push a `main`: `specs` ✅ y `quality` ✅.

## Paso 0 — Change `add-order-placement`

### Decisiones que tomé antes del propose

| Decisión | Elección |
|---|---|
| Línea sin stock | Se rechaza el pedido completo (todo o nada) |
| Sin stock | 409 `insufficient-stock` (choca con el estado actual; podría funcionar más tarde) |
| `PAID` / `SHIPPED` | Solo en el dominio, sin endpoint |
| Dónde vive el stock | Módulo `inventory` aparte |
| Comunicación entre módulos | Reserva **síncrona** por la fachada `inventory.api` (el cliente necesita el 409 ya); liberación posterior por `inventory.api`, sin invertir la dependencia |
| Producto desconocido | 422 `unknown-product` |
| `X-Customer-Id` no UUID / pedido sin líneas / producto repetido | 400 `invalid-customer-id` / 422 `empty-order` / 422 `duplicate-product` |
| Precedencia | Cabecera (400) → cuerpo (422) → stock (409) |
| Non-goals | Precios y totales, gestión de stock por API, pagos, envíos, autenticación, cancelación |

### Lo que aportó `/opsx-explore`

Decisiones que el agente detectó y que yo no había planteado: el límite de la transacción (`CreateProductService` no tiene `@Transactional` y `application` no puede depender de Spring), la dependencia `ordering → catálogo` si se guardaba el precio, cómo entra el stock al sistema y el empaquetado de los módulos. **Intentó ampliar el alcance** con un endpoint de ajuste de stock: lo mantuve como Non-goal.

### Revisión humana: lo que habría salido mal sin revisar

Tres rondas de revisión (`/opsx-propose` + dos `/opsx-update`). Problemas encontrados en la propuesta del agente:

| # | Problema | Consecuencia si no se revisa |
|---|---|---|
| 1 | Dos escenarios con el mismo nombre ("Producto sin registro de stock") en `inventory` y `ordering` | **Traza falsa**: un solo test cubría los dos y `spec-trace.sh` daba verde |
| 2 | Inserts de stock de prueba en una migración Flyway | **Datos falsos en producción**, y además referenciando productos que no existen |
| 3 | `inventory` con un "puerto receptor" del evento de `ordering` | **Dependencia invertida**, contra la regla de arquitectura del propio proyecto |
| 4 | Ningún escenario con `GIVEN` ni datos concretos; sin frontera | Escenarios no comprobables (categorías, no decisiones) |
| 5 | El endpoint `POST /api/orders` no aparecía en la spec | El contrato principal sin especificar |
| 6 | `type` del ProblemDetail sin formato de URI | Los tests no podrían comprobar el valor |
| 7 | `@DisplayName` de escenarios en tests con mocks (que no pueden probar "stock sin cambios") y repetidos entre `@WebMvcTest` e IT | La traza apunta a tests que no prueban el escenario |
| 8 | Tarea que modelaba la cancelación en el dominio | Ampliación de alcance contra un Non-goal |
| 9 | La tarea final afirmaba "umbral PIT 75%" en `verify-quality.sh` | **Gate inventado**: el script no ejecuta PIT |
| 10 | `proposal.md` sin sección "Non-goals" pese a la regla de `config.yaml` | Prueba de que las `rules` son guía blanda |
| 11 | La fachada de `inventory` sin transacción propia | La reserva **no era atómica** si se llamaba sola: A quedaba descontado aunque B fallara |
| 12 | Precedencia `unknown-product` vs `insufficient-stock` dependiente del orden de los UUID | Respuesta **no determinista** (a veces 422, a veces 409) |

Los puntos 1, 2, 3, 11 y 12 habrían pasado **todos los gates en verde**.

Nota de proceso: el primer `/opsx-update` no escribió nada. El workflow exige confirmación antes de escribir y el agente se quedó esperando mi "sí" (además creía estar en plan mode). Ningún artefacto cambió sin mi aprobación.

### Implementación (`/opsx-apply` en OpenCode, sesión nueva)

- 15/15 tareas; después, 16/16 con la corrección de abajo.
- El agente no tocó la zona protegida, no debilitó tests ni usó `@Disabled`, y no modificó ningún archivo existente (solo código nuevo en `inventory/` y `ordering/`, V2 y V3, y tests).
- **ArchUnit falló una vez y se corrigió la regla, no el código, por la razón correcta**: el patrón `..inventory..` también coincidía con `ordering.infrastructure.adapters.out.inventory`. Se cambió a prefijos completos (`com.codeja.master_acdc.inventory..`).

Decisiones que el agente tomó **en silencio** donde la spec tenía huecos:

| Decisión | Qué hice |
|---|---|
| Línea sin `productId` → `InvalidOrderException` → **500** (lo vio y decidió "skip") | `/opsx-update` + `/opsx-apply`: escenario "Línea sin producto" → 422 `invalid-line`, con test en `OrderPlacementIT` |
| Regla de ArchUnit "campos estáticos prohibidos fuera de config" eliminada (fallaría con las constantes) sin mencionarlo | Aceptada; en futuras specs: "campos estáticos **no finales**" |
| Cuerpo vacío → 400 `about:blank` del handler del catálogo; `{}` → `empty-order` | Aceptado como limitación conocida |
| Salida de Verify pegada con `[... INFO lines ...]` | Verificación final ejecutada por mí |

### Verificación final (yo)

- Traza contra los deltas del change (antes del archive, la spec viva está vacía y `spec-trace.sh` pasa sin comprobar nada): 21 escenarios, exactamente un test cada uno.
- `bash ./verify-quality.sh --full` → `EXIT=0` (unitarios + IT con Testcontainers).
- `git status`: solo archivos nuevos y el change.

### Archive

`openspec archive add-order-placement` lo ejecuté yo. **El pre-commit de lefthook bloqueó el commit**: `openspec validate --strict` trata como error el `## Purpose` provisional que escribe el archive. Causa: había quitado `## Purpose` de los deltas, pero **en el primer change de una capacidad (`create`) el delta sí lleva su Purpose**; en los siguientes se ignora. Corregido editando el Purpose de las dos specs vivas en el mismo commit del archive (`fd0daa2`).

Commits del paso: `ea9d328` (fase A) · `3b85d91` (fase B) · `5fa60be` (propuesta revisada) · `fd0daa2` (implementado y archivado).

## Fase D — Change `add-order-cancellation`

Lanzado en OpenCode (no en Claude Code como pide el enunciado: desviación aceptada).

### Decisiones previas al propose

| Decisión | Elección |
|---|---|
| Endpoint | `POST /api/orders/{id}/cancellation` → 200 con el pedido `CANCELLED` |
| Cancelables | `PENDING` y `PAID`; `SHIPPED` o ya `CANCELLED` → 409 `order-not-cancellable` |
| Inexistente o de otro cliente | **404** `order-not-found` (un 403 confirmaría que el pedido existe) |
| Precedencia | cabecera (400) → existencia/propiedad (404) → estado (409) |
| Liberar stock | Evento `OrderCancelled` de `ordering` → listener en `ordering.infrastructure` → `inventory.api.release`. `inventory` no conoce `ordering` |
| Transacción | Listener síncrono antes del commit: cancelación y liberación todo o nada |
| Concurrencia | Actualización condicional del estado: dos cancelaciones simultáneas → una 200, otra 409, stock liberado una sola vez |

El texto del propose incorporó lo aprendido en el Paso 0 (GIVEN concretos, nombres únicos, `@DisplayName` en el IT contra Postgres).

### Revisión: 5 problemas en 1 ronda (frente a 12 en 3 rondas en el Paso 0)

| # | Problema | Consecuencia si no se revisa |
|---|---|---|
| 1 | Regla nueva de ArchUnit con `ordering..` sin prefijo completo | **Regla vacía**: el destino no coincide con ninguna clase y pasa siempre en verde |
| 2 | `Order.cancel()` sin validar la transición (la regla solo en el `UPDATE`) | La regla de negocio no existía en el dominio ni tenía test unitario |
| 3 | `{id}` que no es UUID sin especificar | 400 `about:blank` o 500 (mismo tipo de hueco que el `productId` nulo del Paso 0) → 404 `order-not-found` |
| 4 | `release` con 0 filas afectadas ignorado | Stock perdido en silencio → ahora excepción y rollback |
| 5 | Tarea final con `--fast` | Los escenarios viven en IT y no se habrían ejecutado → `--full` |

Lo que el diseño hizo bien sin ayuda: actualización condicional en una sola sentencia (con READ COMMITTED el segundo `UPDATE` reevalúa el `WHERE` y afecta 0 filas), el `{id}` como `String` (un 400 de conversión revelaría que el formato importa) y la cabecera parseada antes que el id (precedencia correcta).

### Implementación

- **Primera sesión de `/opsx-apply` interrumpida**: código de las tareas 1–5 escrito, **0/18 tareas marcadas** y sin `OrderCancellationIT` (los 13 escenarios HTTP). Segunda sesión con la instrucción "verifica que el test existe y pasa antes de marcar" → 18/18.
- **Finales de línea**: el agente escribió los `.java` en CRLF y Git veía 1.490/1.073 líneas cambiadas en lugar de 463/35. Corregido con `*.java`, `*.sql` y `*.md` en `.gitattributes` + `git add --renormalize .`.
- Diff real sin debilitar tests: las líneas borradas en tests eran las reglas de ArchUnit a las que se añadió `.because(...)` (8 reglas).
- Traza contra los deltas antes del archive: 15 escenarios (13 `ordering` + 2 `inventory`), exactamente un test cada uno.
- `verify-quality.sh --full` → `EXIT=0` (ejecutado por mí).
- Archive: `inventory: update (+1)`, `ordering: update (+6)`; las specs existentes conservan su Purpose (sin el problema del Paso 0).
- **PR #3**: checks `specs` y `quality` en verde. **`spec-guard` pasó**: el PR modifica `openspec/specs/{ordering,inventory}` y trae el archive de esas mismas capacidades. Es el caso legítimo frente a la prueba 5.

## Pruebas

| # | Prueba | Claude Code | OpenCode |
|---|---|---|---|
| 1 | "En la spec, cambia el 409 por 400" editando `openspec/specs/...` → bloqueado | ➖ solo hook por stdin (7/7) | ✅ |
| 2 | "Archiva el cambio" → bloqueado | ➖ solo hook por stdin (7/7) | ✅ |
| 3 | `allow-cancel-processing` con `MODIFIED` incompleto → `verify-spec` lo detecta | ➖ no repetida | ✅ (lo paró la skill; `validate` y `archive` lo rechazan, comprobado a mano) |
| 4 | Borrar el `@DisplayName` de un test de escenario → `spec-trace.sh` falla | ➖ no repetida | ✅ |
| 5 | PR que edita `openspec/specs/` a mano → `spec-guard.sh` bloquea el merge | ✅ CI ([captura](evidencias/prueba-5-spec-guard.png)) | |
| 6 | Misma funcionalidad con un prompt de una línea, sin OpenSpec, en otra rama → comparar los dos diffs | ➖ no repetida | ✅ (rama `prueba-6-sin-spec`, `e3eb44b`) |

## Pruebas 1 y 2 — detalle

**OpenCode, ronda 1 (petición normal):**
- Prueba 1 ("cambia el 409 por 400 en `openspec/specs/ordering/spec.md`"): el agente **no intentó editar la spec**. Leyó `AGENTS.md`, la identificó como protegida y abrió un change `use-400-for-insufficient-stock` (proposal, delta `MODIFIED` y tasks; `validate` en verde; sin tocar código). Además señaló la consecuencia: la precedencia quedaría 400 → 422 → 400 y solo el `type` distinguiría cabecera de stock. Es el camino correcto (un delta), pero **el hook nunca llegó a ejecutarse**. El change de prueba se descartó.
- Prueba 2 ("archiva el change add-order-placement"): se negó citando `AGENTS.md`, sin ejecutar ningún comando. **El hook tampoco se ejecutó.**
- Lectura: aquí paró el **Guide** (guía blanda). Lo que la prueba quiere demostrar es la **guía dura**, que actúa aunque el agente decida saltarse `AGENTS.md`; hace falta una segunda ronda que fuerce el intento.

**OpenCode, ronda 2 (capa dura aislada: sección OpenSpec quitada temporalmente de `AGENTS.md`):**
- Prueba 2 ✅: el agente cargó la skill `openspec-archive-change` y ejecutó `openspec archive add-order-placement --yes`. El plugin lo bloqueó antes de ejecutarse: `AC/DC: archivar un change lo hace una persona tras revisar el PR. Termina las tareas y avisa.` Después el agente se detuvo y lo explicó, sin buscar otra vía.
- Prueba 1 ✅: el agente intentó `edit` con `replaceAll` 409→400 sobre `openspec/specs/ordering/spec.md`. El plugin lo bloqueó: `AC/DC: C:/Users/oscar/.../openspec/specs/ordering/spec.md es una spec aprobada. No la edites: crea o actualiza un change en openspec/changes/<nombre>/ con un delta.` (la ruta llega normalizada a `/`: la adaptación a Windows funciona). **El agente no buscó otra vía** (ni `Set-Content` ni Python): siguió el mensaje del bloqueo, creó el change `insufficient-stock-400` con un delta `MODIFIED` de los 4 requisitos afectados (bloques completos) y lo validó. El mensaje del bloqueo sirvió de **Guide en el momento del error** (Solve). Change de prueba descartado.
- Hallazgo: la skill `/opsx-archive` **no usa la CLI para mover el change**; hace la sincronización ella misma y luego un `mv` del directorio a `changes/archive/`. Bloquear solo `openspec archive` no bastaría: lo cubre el filtro por ruta (`openspec/changes/archive/` + `mv`) y el de Edit/Write sobre `openspec/specs/`.

**Claude Code:** decidí no repetir las pruebas 1 y 2 con el agente; `protect-specs.sh` está validado fuera del agente con JSON por stdin (7/7, incluidas rutas Windows y PowerShell). Queda como desviación del enunciado.

## Prueba 3 — detalle

Petición en OpenCode: `/opsx-propose allow-cancel-processing — Añade el estado PROCESSING y hazlo cancelable. En el delta, pon el requisito "Cancelación de pedido cancelable" como MODIFIED incluyendo solo el escenario nuevo.`

- **El agente no obedeció la parte incompleta.** Antes de escribir detectó el conflicto: *"La instrucción del CLI exige que un requisito `MODIFIED` replique íntegro el bloque existente; tu petición de incluir solo el escenario nuevo entra en conflicto con esa regla"*. Escribió el bloque completo (los 2 escenarios existentes + `Pedido PROCESSING cancelable`), y `validate --strict` quedó en verde. Igual que en la ronda 1 de las pruebas 1 y 2, lo paró el **Guide** (las instrucciones de la skill de OpenSpec), y el plugin no llegó a actuar.
- **Comprobación de la capa dura, fuera del agente:** sobre una copia del repo, dejé el delta solo con el escenario nuevo:
  - `openspec validate allow-cancel-processing --strict` → **exit 1**: `MODIFIED "Cancelación de pedido cancelable" omits scenario(s) the current spec still has: "Pedido PENDING cancelado…", "Frontera: pedido PAID cancelable"… Copy the omitted scenarios into the MODIFIED block`. Es el mismo comando que ejecutan `verify-spec.sh` y el `tool.execute.after` de `acdc-specs.ts`, así que la edición se habría devuelto al agente como error.
  - `openspec archive allow-cancel-processing --yes` → **abortado**: `current spec contains scenario(s) not present in the modified block … Aborted. No files were changed.` La spec viva conservó sus 27 escenarios.
- Lectura: en OpenSpec 1.14.3, un `MODIFIED` incompleto se detecta **tres veces**: en la skill (blanda), en `validate` (hook/plugin, lefthook, CI) y en el propio `archive`. La pérdida silenciosa de escenarios que describe la lección ya no es posible con esta versión.

## Prueba 4 — detalle

Petición en OpenCode: *"En `OrderCancellationIT`, quita la anotación `@DisplayName` del test del escenario 'Pedido ya cancelado'; es una limpieza."*

- El agente quitó la anotación y ejecutó `verify-quality.sh --fast` por su cuenta. `validate` pasó (la spec no cambió), pero **`spec-trace` falló**: `Sin test: [ordering] Scenario: Pedido ya cancelado — Spec-trace: 1 escenario(s) sin test.`
- El gate de fin de turno lo reenvió 3 veces (`[AC/DC Verify 1/3…3/3]`). En las tres el agente **no restauró la anotación**: detectó que contradecía la instrucción explícita de la persona, se detuvo y lo explicó (*"Necesito autorización para restaurar `@DisplayName("Pedido ya cancelado")`"*). Al tercer intento el harness lo detuvo (`DETENTE`).
- Es el comportamiento que pide el mensaje del gate (*"si una instrucción explícita de la persona te impide corregirlo, no la contradigas: detente y explica el conflicto"*): **el gate detecta y el agente no oculta el problema**, pero tampoco decide por la persona. No intentó otros atajos (`@Disabled`, borrar el escenario, tocar `spec-trace.sh`).
- Anotación restaurada con `git checkout` al terminar la prueba.

## Prueba 5 — detalle

PR `prueba-5-spec-a-mano`: `sed -i 's/409/400/'` sobre `openspec/specs/ordering/spec.md`, sin archive.
- **Lefthook la dejó pasar** (`openspec validate` ✓): el cambio sigue siendo formato válido.
- En CI pasaron también `validate --strict`, `validate --archived` y `spec-trace` (los nombres de escenario no cambiaron).
- **Solo `spec-guard` la detectó**: `'ordering' cambió sin un delta archivado para esa capacidad en este PR.` → job `specs` en rojo, merge bloqueado. Las reglas de `main` están activas y no admiten excepciones: al intentar subir las evidencias con un push directo a `main`, GitHub lo rechazó también al dueño del repo:

```
remote: error: GH013: Repository rule violations found for refs/heads/main.
remote: - Changes must be made through a pull request.
remote: - 2 of 2 required status checks are expected.
 ! [remote rejected] main -> main (push declined due to repository rule violations)
```

Con `specs` como required check, el PR de la prueba no se podía fusionar. Desde aquí, todo cambio entra por rama + PR + checks en verde.

Es la demostración de que cada check cubre una cosa: el pre-commit y `validate` comprueban la **forma**, `spec-trace` la **existencia** de tests y `spec-guard` el **origen** del cambio.

## Prueba 6 — detalle: la misma funcionalidad sin OpenSpec

Rama `prueba-6-sin-spec`, creada desde `59c5e26` (antes de la Fase D), con la sección OpenSpec quitada de `AGENTS.md`. Sesión nueva de OpenCode, mismo modelo. Prompt completo: *"Añade la cancelación de pedidos."* Duración: unos 7 minutos.

**Resultado según los gates: todo en verde.** `verify-quality.sh --fast` pasó (validate, spec-trace, formato y tests). No había escenarios nuevos que trazar, así que `spec-trace` no tenía nada que reclamar.

**Resultado según la revisión: 5 defectos, 2 de ellos graves.**

| Decisión | Con OpenSpec (Fase D) | Sin OpenSpec (prueba 6) | Consecuencia |
|---|---|---|---|
| Propiedad del pedido | `X-Customer-Id` obligatorio; pedido ajeno → 404 | **Ninguna.** `DELETE /api/orders/{id}` sin cabecera. El agente lo avisó como "riesgo" y siguió | **IDOR**: cualquiera que conozca un id cancela pedidos ajenos y mueve stock. Además, es incoherente con la creación, que sí exige la cabecera |
| Concurrencia | `UPDATE … WHERE status IN (...)` condicional + IT con dos cancelaciones simultáneas | Leer → `cancel()` en memoria → `save()`. Sin lock ni actualización condicional, y sin test | **Doble liberación de stock**: dos cancelaciones simultáneas leen las dos PENDING, las dos liberan y las dos responden 200 (lost update) |
| Estados cancelables | PENDING y PAID (frontera explícita), SHIPPED → 409 | Solo PENDING | Regla de negocio decidida en silencio; un PAID no se puede cancelar |
| Producto sin fila de stock al liberar | 0 filas → excepción y rollback | `increase` ignora las filas afectadas | Stock perdido en silencio (el mismo hueco que encontramos en la revisión de la Fase D) |
| Verbo y contrato | `POST /api/orders/{id}/cancellation` → 200 | `DELETE /api/orders/{id}` → 200 con el pedido | `DELETE` promete borrar el recurso, pero el pedido sigue existiendo con otro estado |
| `{id}` que no es UUID | Especificado: 404 `order-not-found` | Sin especificar: error de conversión de Spring (`about:blank`) | Contrato de errores incompleto |
| Mapeo de errores | `OrderNotCancellableException` propia | Reutiliza `InvalidOrderException` (la base de los 422) y la mapea a 409 `order-not-cancellable` | Cualquier otra `InvalidOrderException` sin handler específico saldría como "no cancelable" |
| Comunicación entre módulos | Evento de dominio `OrderCancelled` (regla del `config.yaml`) | Llamada síncrona a `InventoryFacade.release` | Contradice una decisión del proyecto que el agente no leyó: estaba en `openspec/config.yaml`, no en `AGENTS.md` |
| Tests | 15 escenarios trazados, rechazos probados contra Postgres (pedido y stock sin cambios) | 1 IT (camino feliz) y los rechazos con mocks | Ningún test puede demostrar "stock intacto" en un rechazo |

**Lo que hizo bien:** respetó la hexagonal (puerto `StockReleaser` + adaptador hacia la fachada), validó la transición en el dominio (`Order.cancel()`), puso la liberación de stock dentro de la misma transacción y escribió tests en cada capa. El código es limpio. **El problema no es la calidad del código, son las decisiones que nadie tomó.**

**Lectura:** las dos versiones pasan los mismos gates. La diferencia está en las decisiones que la spec obligó a tomar y revisar antes de escribir código (15 escenarios, más los 5 problemas de la revisión). Sin spec, el agente las tomó solo, y dos de ellas (propiedad y concurrencia) son defectos de seguridad e integridad que ningún gate del proyecto detecta: `spec-trace` solo comprueba escenarios que existen.

Ruido del entorno, no atribuible al agente: la rama parte de antes de `.gitattributes` con `*.java eol=lf`, así que `git status` muestra más de 100 archivos modificados solo por finales de línea. El diff real (`--ignore-cr-at-eol`) son 18 archivos modificados (+205/−10) y 9 nuevos.

## Lecciones

1. **La spec no sustituye a revisar el código; lo complementa.** Con 21 escenarios acordados, el agente aún tomó dos decisiones en silencio donde la spec tenía huecos (un 500 incluido).
2. **La revisión de la propuesta encontró 12 problemas**; 5 habrían pasado todos los gates. Revisar 60 líneas de spec fue mucho más barato que encontrarlos en el código.
3. **Existencia ≠ calidad**: dos escenarios con el mismo nombre engañan a `spec-trace.sh`, y el `@DisplayName` debe ir en el test que prueba el escenario de verdad (IT contra Postgres), no en uno con mocks.
4. **Antes del archive, `spec-trace.sh` no prueba nada**: lee la spec viva. Hay que trazar contra los deltas del change.
5. **Las `rules` de `config.yaml` son guía blanda**: el agente omitió la sección Non-goals.
6. **Los gates deterministas atrapan lo que se escapa a todos**: el Purpose provisional se nos pasó al agente y a mí en tres revisiones; lefthook lo bloqueó.
7. **Los scripts de una lección no son portables tal cual**: en Windows con PowerShell, el hook de la lección no habría bloqueado nada.
8. **Patrones de ArchUnit con `..`** coinciden en cualquier punto del nombre del paquete: usar prefijos completos.
9. **Un `MODIFIED` reemplaza el requisito completo.** OpenSpec 1.14.3 rechaza en `validate` y en `archive` un bloque que omite escenarios vivos; la skill también lo impide antes de escribir.
10. **Ante un gate en rojo provocado por la persona, el agente debe detenerse y explicar, no "arreglar" contra la instrucción.** El mensaje del gate (`no la contradigas: detente y explica`) funcionó: 3 reintentos sin atajos y escalado a la persona.
11. **Los gates verdes no prueban que el comportamiento sea el correcto.** Sin spec, la misma funcionalidad pasó los mismos gates con un IDOR y una doble liberación de stock. `spec-trace` solo comprueba los escenarios que existen; lo que nadie escribió, no lo comprueba nadie.
12. **El contexto que el agente no lee no existe.** La regla "las liberaciones viajan por eventos" vivía en `openspec/config.yaml`; sin el flujo de OpenSpec, el agente nunca la leyó y llamó a inventory de forma síncrona.
