# Lab S19 — Event Storming remoto, mapa de contextos y ADR 0013-0015

Rama `lab/s19`. Taller del 2026-11-24, con Oscar como facilitador y negocio simulado por un agente en una
sola sesión (desviación del enunciado: lo recomendado es una sesión por rol, ver
`docs/domain/event-storming.md`).

## Entregables

| Paso | Fichero |
|---|---|
| 1-6 · Tablero (Big Picture, Process Modeling, contornos) | `docs/domain/board/event-storming.excalidraw` (+ `.png`, `.svg`) |
| 7 · Transcripción por flujo, hotspots, tablero frente a código | `docs/domain/event-storming.md` |
| 7 · Glosario | `docs/domain/glossary.md` |
| 6-7 · Contextos, mapa, prueba del cambio y deuda | `docs/domain/context-map.md` (el antiguo `docs/context-map.md` queda como enlace) |
| 8 · ADR | `docs/adr/0013-…`, `0014-…`, `0015-…` e índice `docs/adr/README.md` |
| 9 · Guide | `AGENTS.md`, sección «Dominio y bounded contexts» |
| 9 · Guide (specs) | change `openspec/changes/handle-stock-shortage-after-payment/` (lo propone el agente; ver abajo) |
| 9 · Verify | `ContextMapTest` (8 reglas), `TableOwnershipTest`, `docs/adr/` y `docs/domain/context-map.md` en `protect-verify.sh` y `verify-quality.ts`, tests en `scripts/protected-tests.txt` |

## Números del taller

72 eventos · 5 pivotes · 7 ramas · 11 hotspots (9 del taller + 2 que nacen del ADR 0015) · 9 policies en el
tramo detallado · 10 contextos (6 con módulo).

## Solve: hotspots abiertos (decisiones de negocio, no tareas para un agente)

| # | Pregunta | Responsable |
|---|---|---|
| H1 | Producto retirado mientras el cliente paga: ¿se respeta el pedido? ¿Depende del motivo? | Lucía (ventas), Andrés (atención) |
| H2 | ¿Reembolso al recibir la devolución o al aprobar la revisión? ¿Según el motivo? | Jorge (finanzas), Andrés (atención) |
| H3 | Devolución dañada: ¿quién decide el reembolso parcial y con qué criterio? ¿Quién paga el envío de vuelta? | Jorge (finanzas), Marta (almacén) |
| H4 | Pago rechazado: ¿cuánto se guarda la reserva? ¿Igual en campaña? | Lucía (ventas), Marta (almacén) |
| H6 | Envío devuelto por ausencia: ¿quién paga el reenvío y cuántos intentos hay? | Jorge (finanzas), Andrés (atención) |
| H7 | ¿Hasta cuándo se puede cancelar y quién asume el envío? | Andrés, Marta, Jorge |
| H8 | ¿Con qué precio se cobra si la tarifa cambia con el producto en el carrito? ¿Sobre qué precio va el cupón? | Lucía (ventas), Jorge (finanzas) |
| H9 | ¿Se devuelve el cupón al cancelar o devolver? | Lucía (ventas), Jorge (finanzas) |
| H10 | Rotura total y el cliente elige esperar: ¿plazo máximo de espera? | Lucía (ventas), Marta (almacén) |
| H11 | Tras una rotura, ¿el saldo se ajusta solo o Marta lo ajusta tras recontar? | Marta (almacén) |

Resuelto: **H5** → ADR 0015 (decide Oscar Torrens como dueño del negocio).

## Verify

| Comprobación | Resultado |
|---|---|
| `ContextMapTest` sobre el código de `lab/s19` | 8/8 en verde (`docs/evidencias/s19/context-map-test.txt`) |
| Una mutación por regla de `ContextMapTest` | 8/8 fallan con su mensaje (misma evidencia) |
| `TableOwnershipTest` | todas las tablas tienen dueño; 0 violaciones; 1 deuda (`stock` desde catalog) |
| Hooks (`protect-verify.sh`) | Write/Edit en `docs/adr/` y en `context-map.md` → exit 2; `sed -i` sobre un ADR → exit 2; editar el glosario y `cat` de un ADR → exit 0 |
| `bash ./verify-quality.sh --fast` en Windows (2026-11-24) | verde: `ContextMapTest` 8/8, `TableOwnershipTest` 3/3, `ModuleBoundariesTest` 2/2; 488 tests unitarios y 203 de integración sin fallos |
| Correcciones que pidió Verify | Checkstyle: nombres `should_…_when_…` + `@DisplayName` en `TableOwnershipTest`; PMD `LiteralsFirstInComparisons` en `ContextMapTest` (constante `SHARED_KERNEL_PACKAGE`) |

## Change de OpenSpec `handle-stock-shortage-after-payment` (Guide, specs)

Lo propuso OpenCode a partir del ADR 0015. Tiene 4 escenarios (rotura total con espera, con anulación y sin
respuesta en 48 h, y rotura parcial), deja H10 y H11 como preguntas abiertas y bloquea la implementación
hasta tener las decisiones humanas. Además detectó un conflicto que nadie vio en el taller: la spec
`ordering` (requisito «Reembolso de pedido pagado o enviado») obliga a que un reembolso desde PAID **no
libere stock**, mientras que el ADR 0015 exige que cada cobro anulado libere la reserva. No lo modificó: queda
para decidirlo al separar *Cobro anulado* de *Reembolso*.

## Prueba de la Guide con una sesión nueva del agente

Prompt, sin más contexto:

> Explícame qué ocurre desde que un cliente confirma la compra hasta que el pedido queda confirmado, qué
> módulos intervienen y cuáles no pueden depender de cuáles. Cita los ficheros de los que sacas cada
> afirmación.

Resultado (2026-11-24, `docs/evidencias/s19/opencode-prueba-guide.md`): **la Guide está conectada, con un hueco.**

| Criterio | Resultado |
|---|---|
| No inventa el flujo | ✅ Seis pasos fieles al código (PENDING → PAYING → PAID, importe fijado en el primer intento, rechazo que vuelve a PENDING, outbox → `CatalogStockProjection`), cada uno con fichero y líneas |
| Usa el glosario | ✅ reserva lógica frente a apartado físico, disponibilidad visible, Pagos y riesgo dentro de `ordering`, shared kernel, cobro capturado no implementado |
| Respeta el mapa | ✅ dirección de cada contexto, shared kernel limitado, las tres excepciones y la deuda (incluida la tabla `stock`) |
| Respeta los hotspots | ✅ dice que H8 sigue abierto y no lo da por decidido |
| Cita `docs/domain/` | ✅ glosario, mapa y transcripción |
| Cita `docs/adr/` | ❌ no abrió ningún ADR: reprodujo las reglas del ADR 0014, pero citando el mapa |

Corrección: el mapa de contextos lleva ahora el ADR de cada regla (columna «Por qué»), y `AGENTS.md` pide leer y
citar los ADR 0013 y 0014 al explicar o tocar una frontera.

**Segunda prueba** tras la corrección, en otra sesión nueva (`docs/evidencias/s19/opencode-prueba-guide-2.md`):
✅ abre y cita `docs/adr/0013-…` y `docs/adr/0014-…` (subdominios, shared kernel, consultas por puertos,
efectos por eventos y las tres excepciones), además de `docs/domain/context-map.md` y `event-storming.md`.
Mantiene el flujo fiel al código y añade los hotspots abiertos que afectan al tramo (H4 y H8) con sus
responsables, sin decidirlos. La Guide queda bien conectada.
