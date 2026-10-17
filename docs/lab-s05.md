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

⚠️ Pendiente.

Pendiente heredado del Paso 0: `ArchitectureTest` no tiene ningún `.because(...)`. El enunciado pide la regla de ArchUnit **con su `because`**: añadirlo a las reglas existentes y exigirlo en la regla nueva de la cancelación.

## Pruebas

| # | Prueba | Claude Code | OpenCode |
|---|---|---|---|
| 1 | "En la spec, cambia el 409 por 400" editando `openspec/specs/...` → bloqueado | ⚠️ | ⚠️ |
| 2 | "Archiva el cambio" → bloqueado | ⚠️ | ⚠️ |
| 3 | `allow-cancel-processing` con `MODIFIED` incompleto → `verify-spec` lo detecta | ⚠️ | ⚠️ |
| 4 | Borrar el `@DisplayName` de un test de escenario → `spec-trace.sh` falla | ⚠️ | |
| 5 | PR que edita `openspec/specs/` a mano → `spec-guard.sh` bloquea el merge | ✅ CI ([captura](evidencias/prueba-5-spec-guard.png)) | |
| 6 | Misma funcionalidad con un prompt de una línea, sin OpenSpec, en otra rama → comparar los dos diffs | ⚠️ | |

## Prueba 5 — detalle

PR `prueba-5-spec-a-mano`: `sed -i 's/409/400/'` sobre `openspec/specs/ordering/spec.md`, sin archive.
- **Lefthook la dejó pasar** (`openspec validate` ✓): el cambio sigue siendo formato válido.
- En CI pasaron también `validate --strict`, `validate --archived` y `spec-trace` (los nombres de escenario no cambiaron).
- **Solo `spec-guard` la detectó**: `'ordering' cambió sin un delta archivado para esa capacidad en este PR.` → job `specs` en rojo, merge bloqueado. ⚠️ completar: captura del botón de merge bloqueado.

Es la demostración de que cada check cubre una cosa: el pre-commit y `validate` comprueban la **forma**, `spec-trace` la **existencia** de tests y `spec-guard` el **origen** del cambio.

## Lecciones

1. **La spec no sustituye a revisar el código; lo complementa.** Con 21 escenarios acordados, el agente aún tomó dos decisiones en silencio donde la spec tenía huecos (un 500 incluido).
2. **La revisión de la propuesta encontró 12 problemas**; 5 habrían pasado todos los gates. Revisar 60 líneas de spec fue mucho más barato que encontrarlos en el código.
3. **Existencia ≠ calidad**: dos escenarios con el mismo nombre engañan a `spec-trace.sh`, y el `@DisplayName` debe ir en el test que prueba el escenario de verdad (IT contra Postgres), no en uno con mocks.
4. **Antes del archive, `spec-trace.sh` no prueba nada**: lee la spec viva. Hay que trazar contra los deltas del change.
5. **Las `rules` de `config.yaml` son guía blanda**: el agente omitió la sección Non-goals.
6. **Los gates deterministas atrapan lo que se escapa a todos**: el Purpose provisional se nos pasó al agente y a mí en tres revisiones; lefthook lo bloqueó.
7. **Los scripts de una lección no son portables tal cual**: en Windows con PowerShell, el hook de la lección no habría bloqueado nada.
8. **Patrones de ArchUnit con `..`** coinciden en cualquier punto del nombre del paquete: usar prefijos completos.
