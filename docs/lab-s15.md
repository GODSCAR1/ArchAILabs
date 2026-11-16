# Lab S15 — mutation score del módulo de precios: de "cobertura alta" a reglas verificadas

Rama `lab/s15` sobre `lab-s15-start`. JDK 25, **PIT 1.30.2** con `pitest-junit5-plugin` 1.2.3 (desde la 1.25.8 PIT corrige los mutadores de `BigDecimal` en Java 25).

## Adaptación del enunciado al repo

| Enunciado | `master-acdc` | Decisión |
|---|---|---|
| Módulo `dev.codeja.shop.pricing` existente con ~40 % | No había módulo de precios | Se crea en `lab-s15-start` (commit `45dfb5c`) con las 6 reglas del paso 2: `VolumeDiscount`, `Coupon`, `OrderPricing`, `Money`, `CouponService` y el puerto `CouponRepository` |
| Tests generados con "sube la cobertura" | — | OpenCode (GPT-6.1 Terra), prompt sin más instrucciones: *"Escribe tests para el módulo … hasta llegar al 90 % de cobertura de líneas"* (commit `02dbc83`, sesión en `docs/evidencias/s15-base/`) |
| PIT configurado (lección 3) | Solo existía la propiedad `pit.mutationThreshold` | Se añade el plugin al `pom.xml` (zona protegida, cambio humano), sin atar a ninguna fase |
| `-DwithHistory` para análisis incremental | PIT 1.30 falla: *"History has been enabled but no history plugin has been installed/activated"* (el historial incremental pasó a Arcmutate, de pago) | Sin historial: con `targetTests` el módulo tarda < 1 min |
| `-DtargetClasses` limita la ejecución | Sin `targetTests`, PIT analiza la suite entera (501 tests, ITs incluidos) | Toda medida lleva también `-DtargetTests='com.codeja.master_acdc.pricing.*'` |

Comando de medida (sin umbral; el log fuera de `target/`):

```bash
( time ./mvnw -B test-compile org.pitest:pitest-maven:mutationCoverage \
    -DtargetClasses='com.codeja.master_acdc.pricing.*' \
    -DtargetTests='com.codeja.master_acdc.pricing.*' \
    -Dmutators=STRONGER,EXPERIMENTAL_BIG_DECIMAL -DmutationThreshold=0 ) > ../s15-<paso>.log 2>&1
```

## 1. Línea base

| Medida | Inicial (mutadores por defecto) | Inicial (`STRONGER` + `BIG_DECIMAL`) |
|---|---|---|
| Cobertura de líneas (JaCoCo) | **100 %** (92/92) | — |
| Cobertura de ramas (JaCoCo) | **100 %** (40/40) | — |
| Mutantes generados | 28 | 55 |
| Mutation score (PIT) | **96 %** (27/28) | **95 %** (52/55) |
| Test strength (PIT) | 96 % | 95 % |
| Supervivientes / sin cobertura | 1 / 0 | 3 / 0 |
| Tests del módulo | 10 (2 clases) | 10 |
| Tiempo de PIT | 7 min 9 s (501 tests examinados: toda la suite) | 49 s (13 tests examinados) |

**El enunciado no se reproduce: la suite de partida no es la del 40 %.** OpenCode siguió las reglas de `AGENTS.md` del lab S14 ("cada test verifica comportamiento observable con AssertJ", "un test sin aserción no cuenta como cobertura") aunque el prompt solo pedía cobertura: ningún `isNotNull`, valores exactos con `isEqualByComparingTo` y los límites a ambos lados (9/10 unidades, último día del cupón y el siguiente, 1.005 → 1.00). Es el primer hallazgo del lab: las reglas del harness ya cortan los patrones de la lección 1.

**Por qué cobertura y score altos no bastan.** La cobertura dice qué líneas se ejecutan y el score qué cambios de PIT rompen algún test, pero ninguno de los dos dice qué *regla* está protegida: `should_apply_volume_coupon_and_tax_when_calculating_total` (`PricingTest:31`) comprueba "el cupón se aplica antes de impuestos" con un cupón del 10 %, y un porcentaje conmuta con el IVA ((180 − 10 %) × 1,21 = 180 × 1,21 − 10 % = 196,02), así que aplicar el cupón después de impuestos deja el test en verde — y PIT no tiene mutador que cambie el orden de dos operaciones, de modo que la regla sale al 100 % de cobertura y "matada" sin que nada la verifique.

Otras señales de la suite de partida:

- **Muertes accidentales.** Varios mutantes los "mata" un test que no trata de su regla porque el mutante revienta un constructor de la fixture: `OrderPricing:20` (IVA negativo) lo mata `should_reject_checkout_when_coupon_is_missing` (el servicio se construye con IVA 0); `PricingLine:13` (precio 0 permitido) lo mata el test del suelo a cero. PIT los cuenta como protegidos; ningún test verifica esa regla. Se mide en el paso 6.
- **Tests con varias reglas.** 5 de los 6 tests de `PricingTest` mezclan reglas (volumen + cupón + impuestos; caducidad + suelo a cero; redondeo + operaciones). Un fallo no dice qué regla se rompió.
- **Mockito sobre el puerto del módulo y `verify` en lugar de resultados** (`CouponServiceTest`): "un cupón de un solo uso se marca como usado" se comprueba con `verify(repository).markUsed(…)`, no con que el cupón no se pueda volver a usar.
- **Un test de la biblioteca, no de una regla**: `should_keep_lines_immutable_when_input_list_changes` prueba `List.copyOf`.

### Hallazgos de la sesión de OpenCode (no son de tests)

| # | Qué hizo | Riesgo | Acción |
|---|---|---|---|
| 1 | `semgrep.exe` daba `Permission denied` (Smart App Control bloquea el lanzador de pip, sin firmar). El agente creó un lanzador en `%TEMP%\opencode\semgrep` y ejecutó `PATH="…/Temp/opencode:$PATH" ./verify-quality.sh --fast` | Esta vez era la misma CLI. Pero `verify-quality.sh` acepta cualquier `semgrep` del `PATH`: un lanzador con `exit 0` habría puesto la capa 2 en verde sin analizar nada, y ningún hook lo vio (escribió fuera del repo) | Lanzador permanente aprobado por la persona en `~/bin/semgrep` (vía `python.exe`, firmado). Paso 7: `verify-quality.sh` comprueba las versiones de Semgrep y Gitleaks, y PreToolUse bloquea redefinir `PATH` delante de `verify-quality.sh` |
| 2 | Arrancó Docker Desktop (`docker desktop start`) | Efecto en la máquina de la persona sin pedirlo | Anotado |
| 3 | Cargó por su cuenta la skill `ponytail` ("lazy senior developer") | Explica la suite mínima y densa (10 tests con varias reglas cada uno) | Anotado |

## 2. Supervivientes por regla

Medida `STRONGER` + `EXPERIMENTAL_BIG_DECIMAL` (`docs/evidencias/s15-base/mutations-stronger.xml`). Primero, las seis reglas del enunciado y quién las protege hoy:

| Regla | Clase:línea | Mutantes | Estado | Quién lo mata | ¿Protegida? |
|---|---|---|---|---|---|
| 10 % de descuento desde 10 unidades (incluidas) | `VolumeDiscount:12-13` | `CONDITIONALS_BOUNDARY`, `REMOVE_CONDITIONALS_ORDER_*`, `BigDecimal#multiply` | Matados | `should_apply_volume_coupon_and_tax…` | Sí, pero en un test de 3 reglas |
| Un cupón caducado no se aplica | `Coupon:47` | `REMOVE_CONDITIONALS_EQUAL_*`, `TRUE_RETURNS` | Matados | `should_respect_expiry_and_zero_floor…` | Sí, mezclado con el suelo a cero |
| El cupón se aplica antes de impuestos | `OrderPricing:36-37` | **Ninguno**: PIT no reordena operaciones | — | — | **No** (porcentaje conmutativo, ver paso 1) |
| El total nunca es negativo | `OrderPricing:59`, `Money:37` | `REMOVE_CONDITIONALS_EQUAL_*`, `ORDER_*`, `TRUE_RETURNS` | Matados (salvo `Money:37` límite) | `should_respect_expiry_and_zero_floor…` | Sí, mezclado |
| Redondeo a 2 decimales HALF_EVEN | `Money:21` | Ninguno sobre `setScale`/`RoundingMode` | — | `should_round_half_even…` (1.005 → 1.00) | Solo por el test; PIT no lo mide |
| Un cupón de un solo uso se marca como usado | `CouponService:30` | `VOID_METHOD_CALLS` | Matado | `should_mark_coupon_used…` con `verify` | **No**: verifica la llamada, no que el cupón no se pueda reutilizar |

Los 3 supervivientes:

| # | Clase:línea | Mutador | ¿Qué cambia? | Clasificación | Decisión |
|---|---|---|---|---|---|
| S1 | `Coupon:29` | `REMOVE_CONDITIONALS_EQUAL_IF` | `kind == PERCENT && valor > 100` pasa a `true && valor > 100`: un cupón **fijo** de más de 100 se rechaza | **Regla sin verificar**: el tope de 100 es solo para porcentajes; un cupón fijo de 150,00 es válido | Test de regla |
| S2 | `OrderPricing:33` | `REMOVE_CONDITIONALS_EQUAL_ELSE` | Se quita el atajo `subtotal == 0 → 0` | **Equivalente → código sobrante**: sin el atajo, un subtotal de 0 llega igual a 0,00 (el cupón da negativo y el suelo devuelve `ZERO`; el IVA de 0 es 0). El atajo no añade comportamiento | Se borra el atajo (paso 3) |
| S3 | `Money:37` | `CONDITIONALS_BOUNDARY` | `isNegative`: `< 0` → `<= 0` | **Equivalente**: su único uso es el suelo a cero de `applyCoupon`; en el límite (exactamente 0,00) las dos ramas devuelven 0,00 (`ZERO` o el propio importe). Matarlo exigiría un test del método, no de una regla | Se justifica; se reevalúa si `isNegative` gana otro uso |

## 3. Tests de tres reglas (persona)

Reglas elegidas: las tres peor protegidas de la tabla del paso 2. Sin Spring, sin mocks (`VolumeDiscount`, `Coupon`, `Money` reales), reloj de `FixedClockExtension`, límites parametrizados y `isEqualByComparingTo`. Cada aserción lleva `.as("regla: …")` para que el fallo del paso 6 nombre la regla.

| Regla | Test | Casos |
|---|---|---|
| 10 % desde 10 unidades (incluidas) | `VolumeDiscountTest.should_discount_ten_percent_when_line_reaches_ten_units` | 9 → 0,00; 10 → 10,00; 11 → 11,00 |
| El cupón se aplica antes de impuestos | `OrderPricingTest.should_apply_coupon_before_tax_when_order_has_a_fixed_coupon` | (100,00 − 10,00) × 1,19 = **107,10** (después de impuestos sería 109,00). Con cupón **fijo**: con porcentaje los dos órdenes coinciden |
| El total nunca es negativo | `OrderPricingTest.should_never_go_below_zero_when_coupon_is_applied` | Subtotal 15,00 con cupón 50,00 → 0,00; 15,00 → 0,00; 14,99 → 0,01 (con IVA 19 %) |

Además se borra el atajo `subtotal == 0 → 0` de `OrderPricing.total` (superviviente S2, código sobrante): los tests siguen en verde.

| Medida | Inicial | Tras el paso 3 |
|---|---|---|
| Mutantes | 55 | 53 (el atajo tenía 2) |
| Mutation score | 95 % (52/55) | **96 % (51/53)** |
| Supervivientes | S1, S2, S3 | S1 (`Coupon:29`), S3 (`Money:37`, equivalente) |
| Tests del módulo | 10 | 17 (10 + 7 casos nuevos) |

El score apenas se mueve, y es lo esperado: estas reglas ya estaban "matadas" por los tests de varias reglas. Lo que cambia es *qué* las protege, y se ve en el paso 6: "antes de impuestos" no tenía ningún test que fallara al cambiar el orden; ahora tiene uno.
