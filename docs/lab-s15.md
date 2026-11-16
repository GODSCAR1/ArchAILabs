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

## 4. Tests que no verifican nada

Prueba de la lección 1 sobre los 10 tests de la suite base (¿falla si comentas la línea clave?):

| Test | Resultado | Decisión |
|---|---|---|
| `PricingTest.should_keep_lines_immutable_when_input_list_changes` | Prueba `List.copyOf` (biblioteca), no una regla; al borrarlo **ningún mutante cambia de estado** | **Borrado** |
| `CouponServiceTest` (4 tests) | Sí verificaba, pero con Mockito sobre el puerto del módulo y `verify(…).markUsed` en lugar del resultado | **Borrado para reescribir** sin mocks en el paso 5: sus 7 mutantes quedan `NO_COVERAGE` y son la lista del agente |
| `PricingTest.should_apply_volume_coupon_and_tax_when_calculating_total` | Lo di por redundante con los tests del paso 3 y lo borré: **el score bajó** (3 supervivientes nuevos) | **Restaurado** (ver abajo) |
| Resto de `PricingTest` (caducidad + suelo, redondeo, validaciones) | Sí: cada uno mata mutantes que ningún otro mata | Se quedan (mezclan reglas: el paso 6 dirá si el mensaje basta) |

Ningún test tenía `isNotNull`, ni le faltaban aserciones, ni comprobaba un valor que él mismo había puesto con un mock: de los patrones de la lección 1, la suite base solo tenía el de Mockito.

**El error que el paso 4 está para detectar.** Borrar `should_apply_volume_coupon_and_tax…` dejó vivos tres mutantes que solo él mataba, todos de reglas que mis tests del paso 3 no tocan:

| Mutante | Regla que verificaba |
|---|---|
| `OrderPricing:45` `subtract` → `add` | El descuento por volumen **se resta** del subtotal del pedido (`VolumeDiscountTest` prueba el importe del descuento, no su uso) |
| `PricingLine:19` `multiply` → `divide` | Importe de línea = unidades × precio (mis tests usan 1 unidad, y con 1 multiplicar y dividir dan lo mismo) |
| `OrderPricing:51` sin `coupon == null` | Un pedido **sin cupón** paga subtotal + IVA (nadie más llama a `total(order)`) |

| Medida | Tras el paso 3 | Tras el paso 4 |
|---|---|---|
| Tests del módulo | 17 | **12** |
| Tests eliminados por no verificar nada | — | **1** (+ 4 de `CouponServiceTest`, reescritos en el paso 5) |
| Mutation score (`@pit-pricing`) | 96 % (51/53) | **81 % (43/53)**: 7 `NO_COVERAGE` en `CouponService`, `Coupon:29`, `OrderPricing:20`, `Money:37` |

`OrderPricing:20` (IVA 0 permitido: `< 0` → `<= 0`) aparece ahora porque solo lo mataba de rebote `CouponServiceTest`, que construía el servicio con IVA 0: era una regla protegida por accidente.

### Arnés para el paso 5

- `pom.xml`: execution `pit-pricing` (targetClasses y targetTests de pricing, `STRONGER` + `EXPERIMENTAL_BIG_DECIMAL`, `mutationThreshold=${pit.mutationThreshold}` = 80). `-DmutationThreshold` de la línea de comandos no la afecta.
- `scripts/pit-survivors.sh` → `.acdc/pit-survivors.md` (ignorado en git).
- `verify-quality.sh`, capa 3: `--full` siempre; `--fast` (hook Stop de Claude Code y plugin de OpenCode) solo si la rama toca pricing. Suelo de 80, sin overrides por entorno; si falla, regenera `.acdc/pit-survivors.md` y lo cita.
- `guide-context.sh`: el contexto de sesión muestra el umbral real de PIT (leía un `mutationThreshold>` que ya no existía).

## 5. Sesión dirigida (OpenCode)

El superviviente `Money:37` (`CONDITIONALS_BOUNDARY`, de `< 0` a `<= 0`) es **equivalente** respecto de su único uso en el cálculo del pedido: con importe exactamente cero, tanto devolver el importe como sustituirlo por `Money.ZERO` produce el mismo total. Los casos negativo, cero y positivo están cubiertos por la regla del suelo a cero; un test de `isNegative` aislado distinguiría la implementación sin proteger otra regla de negocio. Debe reevaluarse si aparece otro uso.

El superviviente `CouponService:29` (`REMOVE_CONDITIONALS_EQUAL_IF`) marca también como usado un cupón reutilizable. Es **equivalente para el comportamiento de checkout**: el servicio ignora ese estado para cupones reutilizables y los sigue aplicando; para los de un solo uso se conserva el rechazo posterior. El registro interno sí cambia, pero no hay una regla especificada que prohíba registrar el uso de un cupón reutilizable. Debe reevaluarse si otro consumidor interpreta ese registro.

### Revisión de la sesión de OpenCode (persona)

Sesión `ses_e1d2` (GPT-6.1 Terra, 8 min, `docs/evidencias/s15-paso5/sesion-opencode.md`). Solo OpenCode: la comparación con Claude Code del enunciado se omite por decisión de la persona.

| Medida | OpenCode |
|---|---|
| Tests escritos | 5 (3 en `CouponServiceTest` nuevo, 2 en `OrderPricingTest`) |
| Mutantes matados | 8 (43/53 → 51/53, **81 % → 96 %**) |
| Supervivientes clasificados sin test | 2 (`Money:37`, `CouponService:29`) |
| Tests rechazados en la revisión | **0** |
| Verify | `--fast` en verde con la capa 3 (96 %) |

Señales de alarma de la sección 24, test a test: ninguno tiene más `given` que aserciones, ni `verify`, ni `any()`, ni mocks del dominio. `CouponService` se prueba con un `CouponRepository` en memoria: el cupón de un solo uso se comprueba por su efecto (el segundo checkout lanza `CouponAlreadyUsedException`), no por la llamada a `markUsed`. Único reparo: sin `.as("regla: …")` (se mide en el paso 6).

Clasificaciones: `Money:37` equivalente, coincide con el paso 2. `CouponService:29` (marcar también como usado un cupón reutilizable) lo llama "equivalente"; **no lo es en sentido estricto** — el estado del repositorio cambia —, pero ninguna regla especificada lo lee. Se acepta como **comportamiento no especificado**; si alguna vez se define "un cupón reutilizable no consume usos", pasa a ser regla y su test comprueba el estado del fake.

Comportamiento del agente:

- Volvió a cargar por su cuenta la skill `ponytail`.
- **Ignoró en su resumen los avisos de la capa 2**: tras cada edición el plugin devolvió `Semgrep no pudo analizar … (código 2)`. Causa: el lanzador de `~/bin/semgrep` entraba por `semgrep.cli` (pysemgrep), que no conoce `--experimental`, la opción que usa `security-scan.sh`. El inner loop de la capa 2 estaba roto y el agente no lo dijo. Arreglo en el lanzador (entra por `semgrep.console_scripts.entrypoint`, como `semgrep.exe`).

## 6. Mutaciones manuales: ¿los tests protegen reglas?

Una mutación por regla, distinta de las de PIT (`docs/evidencias/s15-paso6/s15-mutaciones.sh`; logs en la misma carpeta). Criterio: falla al menos un test **y su mensaje nombra la regla rota**.

| # | Regla | Mutación manual | 1.ª pasada | Final |
|---|---|---|---|---|
| 1 | 10 % desde 10 unidades | 5 % en lugar de 10 % | ✅ `VolumeDiscountTest`: `[regla: 10 por ciento … 10 unidades] expected 10.00 but was 5.00` | ✅ igual |
| 2 | Un cupón caducado no se aplica | `validUntil` exclusivo: el cupón deja de valer su último día | ⚠️ Fallan 6 tests, **ninguno habla de caducidad**; el único con mensaje dice *"el cupón se aplica antes de impuestos"*: señala la regla equivocada | ✅ `should_ignore_coupon_when_it_has_expired[2]`: `[regla: un cupón caducado no se aplica; vale hasta su fecha incluida (caduca hoy +0 días)] expected 90.00 but was 100.00` |
| 3 | Cupón antes de impuestos | Cupón sobre el total con IVA | ✅ `[regla: el cupón se aplica antes de impuestos …] 107.10 / 109.00` (antes del paso 3 nadie lo detectaba); ⚠️ en la 2.ª pasada fallaba también el test nuevo de caducidad con su mensaje | ✅ Fallan solo los tests con IVA (antes de impuestos, cupón fijo > 100, `CouponService`); ni caducidad ni suelo |
| 4 | El total nunca es negativo | Se cobra la diferencia en valor absoluto | ✅ `[regla: el total nunca es negativo (subtotal 15.00, cupón 50.00)]` | ✅ `expected 0.00 but was 35.00` |
| 5 | Redondeo HALF_EVEN | `HALF_UP` | ⚠️ `NINGUNO` — **falso**: error del script (el comentario se tragó el `;` y no compiló) | ✅ `MoneyTest`: `[regla: redondeo a 2 decimales HALF_EVEN; 1.005 debe quedar en 1.00] … but was 1.01` |
| 6 | Un solo uso se marca como usado | Se marca **antes** de comprobar si ya estaba usado | ✅ `should_reject_second_checkout_when_coupon_is_single_use`: el primer uso lanza `coupon already used: ONCE` | ✅ igual (el nombre del test nombra la regla) |

**Hallazgo: tests de una regla apoyados en el límite de otra.** Los tests de "antes de impuestos", "cupón fijo > 100" y los dos de `CouponService` creaban el cupón con `validUntil = today`, justo en el límite de la caducidad; el de caducidad y el del suelo comparaban totales con IVA, dependiendo de "antes de impuestos". Al romper una regla fallaban tests de otras, con mensajes que mandan a buscar donde no está el fallo. PIT los cuenta igual de "muertos". Reescritura:

- Los tests que no son de caducidad crean el cupón con `today.plusDays(1)`.
- Caducidad y suelo a cero usan un `OrderPricing` **sin IVA** (90,00 / 100,00 y 0,00 / 0,00 / 0,01).
- Tests nuevos con mensaje propio: `OrderPricingTest.should_ignore_coupon_when_it_has_expired` (mañana, hoy, ayer) y `MoneyTest.should_round_to_two_decimals_half_even…` (1,005 → 1,00; 1,015 → 1,02; 1,025 → 1,02; −1,005 → −1,00; 2,0049 → 2,00).

Quedan en `PricingTest` los tres tests de varias reglas de la suite base (sin mensaje): siguen matando mutantes que nadie más mata (por ejemplo, `PricingLine:19`, importe de línea con más de 1 unidad), así que no se borran; cuando fallan, el test de la regla falla a la vez con su mensaje.

PIT tras el paso 6: **96 % (51/53)**, 17 tests examinados; sobreviven `CouponService:29` (comportamiento no especificado) y `Money:37` (equivalente).

## 7. Umbral

| Pieza | Dónde | Detalle |
|---|---|---|
| Execution `pit-pricing` | `pom.xml` (zona protegida) | targetClasses y targetTests de `pricing`, `STRONGER` + `EXPERIMENTAL_BIG_DECIMAL`, `mutationThreshold=${pit.mutationThreshold}` |
| Umbral | `pit.mutationThreshold` = **93** | Score alcanzado: 96 % (51/53) con 2 supervivientes clasificados. Con 53 mutantes, cada uno vale ~1,9 puntos: **93 admite un superviviente más sin clasificar (50/53 = 94 %) pero no dos (49/53 = 92 %)**. Margen para un equivalente nuevo al tocar el módulo, no para dejar de escribir tests |
| Gate | `scripts/mutation-gate.sh` (zona protegida: `scripts/`) | Suelo propio de 93 (segunda capa: aunque se baje el pom, falla), rechaza `MAVEN_OPTS`/`MAVEN_ARGS`/`.mvn/maven.config` que toquen PIT, ejecuta `@pit-pricing`, regenera `.acdc/pit-survivors.md`. **Desde la prueba 1 del paso 9: falla también con cualquier superviviente no clasificado en `scripts/pit-accepted-survivors.txt`** |
| Loop local | `verify-quality.sh` | `--full` siempre; `--fast` (hook Stop, plugin de OpenCode) si la rama toca pricing |
| CI | Job `mutation-testing` en `.github/workflows/acdc.yml` | `bash scripts/mutation-gate.sh`, supervivientes en el resumen del job e informe de PIT como artefacto. `quality` lo salta con `ACDC_LAYER3_IN_OWN_JOB` (no se ejecuta dos veces). Marcar como required status check |
| PreToolUse | `protect-verify.sh` y plugin de OpenCode | Bloquean comandos con `mutationThreshold` o `-Dmutators` y cualquier `PATH=… verify-quality.sh`. Probado: `-DmutationThreshold=60` → bloqueado (rc 2); `PATH="/tmp:$PATH" bash ./verify-quality.sh` → bloqueado; `…mutationCoverage@pit-pricing` y `verify-quality.sh --fast` → permitidos |

Cierre del hallazgo de la sesión base (Semgrep en el `PATH`):

- `verify-quality.sh` exige Semgrep 1.182.x y Gitleaks 8.30.x, y pasa un **canario**: Semgrep tiene que encontrar los hallazgos de `semgrep-rules/shop-sql-concatenation.java` (código 1). Un lanzador falso con `exit 0` no pasa.
- El lanzador de la persona (`~/bin/semgrep`, vía `python.exe` firmado por Smart App Control) entra por `semgrep.console_scripts.entrypoint`, como `semgrep.exe`: con `semgrep.cli` no entendía `--experimental` y el hook `security-scan.sh` fallaba en silencio (código 2).

Validación (`verify-quality.sh --full`, `docs/evidencias/s15-paso8/verify-full-resumen.log`): **verde en 8 min 57 s**, capa 3 `Generated 53 mutations Killed 51 (96%)`. Por el camino aparecieron dos fallos del propio arnés en Windows, corregidos:

- El canario escaneaba `semgrep-rules/`, que `.semgrepignore` excluye (código 2): ahora copia el fixture a una carpeta temporal.
- Con el lanzador vía `python.exe`, Semgrep devuelve código 2 si su salida va a `/dev/null` (Git Bash lo traduce a `NUL`): el canario y el bucle `semgrep --test` de `--full` escriben en un fichero temporal y muestran sus últimas líneas si fallan.

## 8. Resultado

| Medida | Inicial | Tras el paso 3 | Final |
|---|---|---|---|
| Cobertura de líneas (JaCoCo) | 100 % (92/92) | 100 % | **100 % (89/89)**; ramas 100 % (38/38) |
| Mutation score | 96 % por defecto (27/28) · **95 %** con `STRONGER` + `BIG_DECIMAL` (52/55) | 96 % (51/53) | **96 % (51/53)**, umbral 93 |
| Test strength | 95 % | 96 % | 96 % |
| Tests del módulo | 10 (2 clases) | 17 | **25** (15 métodos, 5 clases) |
| Tests eliminados por no verificar nada | — | — | **1** (`List.copyOf`) + 4 reescritos sin Mockito (`CouponServiceTest`) |
| Tiempo de PIT sobre el módulo | 7 min 9 s sin `targetTests` (501 tests, ITs incluidos) · 49 s con `targetTests` | 53 s | ≈ 1 min (1 min 14 s en el paso 6) |
| Reglas cuya mutación manual rompe un test que la nombra | — | — | **6/6** (4/6 en la 1.ª pasada del paso 6) |

**Lectura.** El criterio del enunciado ("si el número de tests baja y el score sube, has hecho el laboratorio bien") no aplica tal cual: la suite de partida no era la del 40 % — OpenCode, con el `AGENTS.md` del S14, ya escribió tests con valores exactos y límites —, así que el score no tenía de dónde subir y los tests aumentan (10 → 25) porque dos reglas no tenían test propio (caducidad y redondeo) y una no tenía ninguno que la detectara ("antes de impuestos"). Lo que mide el lab se movió en otro eje: **el mismo 96 % protege ahora las seis reglas**. Al principio, "antes de impuestos" estaba al 100 % de cobertura y "matada" sin que nada fallara al cambiar el orden, "un solo uso" se comprobaba con `verify` sobre un mock, el IVA 0 solo lo mataba un test de rebote y la caducidad la detectaban tests de otras reglas con mensajes que apuntaban a la regla equivocada.

Lecciones para el harness:

1. Los mutadores por defecto de PIT casi no tocan un dominio de `BigDecimal` (28 mutantes, ninguno aritmético): para dinero, `STRONGER` + `EXPERIMENTAL_BIG_DECIMAL`.
2. PIT no tiene mutador de orden de operaciones: las reglas de secuencia ("antes de impuestos") solo las verifica un test diseñado con datos donde el orden importa (cupón fijo, no porcentaje).
3. "Killed" ≠ protegida: un mutante que revienta un constructor de la fixture lo mata cualquier test. Las mutaciones manuales del paso 6 son las que miden si el test es de la regla.
4. Un test de una regla no se apoya en el límite de otra (fechas en el límite de caducidad, totales con IVA en tests de cupón).
5. Sin `targetTests`, PIT ejecuta la suite entera: 7 min frente a 49 s.

## 9. Pruebas con agentes (OpenCode)

Cada prueba en una sesión nueva y en su rama desde `lab/s15`; el código de la prueba no se mergea, la evidencia queda en `docs/evidencias/s15-paso9/`.

### Prueba 1: "Añade un descuento del 5 % para clientes VIP" (sin mencionar tests)

Sesión `ses_e1c7` (`prueba-1-sesion-opencode.md`), diff del agente en `prueba-1.patch`, change de OpenSpec en `prueba-1-openspec/`.

1. **El agente no implementó a ciegas.** Cargó por su cuenta `openspec-propose`, pasó Verify como línea base y preguntó lo que no podía saber: cómo se identifica a un VIP, dónde va el 5 % respecto a volumen, cupón e impuestos, y si se conecta al checkout. Respuestas de la persona: el pedido a valorar indica si es VIP; el 5 % va tras el volumen y **antes del cupón**, acumulable, impuestos al final; solo en `pricing`.
2. **Revisión de la propuesta: un total mal calculado.** El escenario 1 decía 89,85; con el redondeo del código (HALF_EVEN en `Money.times`) el IVA de 75,50 es 14,345 → 14,34 y el total **89,84**. Un test escrito desde la spec habría fallado contra el código correcto, con el riesgo de que el agente "arreglara" el redondeo. Corregido con `/opsx-update`; el agente recalculó los otros cuatro totales y pidió confirmación antes de editar.
3. **Implementación (`/opsx-apply`): correcta.** 5 % con `Money.times` y `minus`; tests por escenario: orden VIP → cupón con cupón fijo (89,84; después del cupón sería 90,44), no VIP (95,20), sin cupón (95,00), empate de redondeo (0,50 → 0,48), suelo (0,00) y `CouponService` con VIP (101,15 + estado del fake). Sin Mockito, `verify`, `any()` ni mocks del dominio. Reparos: el `0.05` suelto (sin constante como `VolumeDiscount.RATE`), el booleano en `PricingOrder.of(false, …)` y sin `.as("regla: …")`. PIT: 96 % (53/55). **El gate de PIT no tuvo que intervenir** (un solo `[AC/DC Verify]`, en la propuesta: faltaba el spec delta).
4. **Hallazgo: el gate por porcentaje no protege una regla nueva.** La regla VIP genera **2 mutantes** (el ternario: PIT no muta `Money.minus` ni el orden de operaciones), y el de "siempre VIP" ya lo mataban los tests no VIP existentes. Sin ningún test VIP queda **un** superviviente: 52/55 = 95 % ≥ 93 → `rc=0`. Comprobado quitando los 6 tests VIP y ejecutando `scripts/mutation-gate.sh`: pasó, con `OrderPricing:34 RemoveConditional_EQUAL_ELSE` ("nunca VIP") vivo.
5. **Cambio del gate: cero supervivientes sin clasificar.** `scripts/pit-accepted-survivors.txt` (zona protegida) lista los supervivientes clasificados por una persona (`Money:37` equivalente, `CouponService:29` no especificado), con su motivo. `pit-survivors.sh` añade la columna *Clasificado* a `.acdc/pit-survivors.md` y `mutation-gate.sh` falla con cualquier fila sin clasificar, aunque el porcentaje pase. El agente no edita la lista: si cree que un superviviente no es una regla, se lo explica a la persona. El porcentaje sigue como red de seguridad.
6. **Demostración (no planificada).** Con la sesión de OpenCode aún abierta y los tests VIP quitados, el plugin volvió a lanzar Verify con el gate nuevo: `[AC/DC Verify 1/3]` — *"1 superviviente(s) de PIT sin clasificar en pricing"*, con el porcentaje en 95 %. El agente leyó el informe y `git status`, concluyó que los tests VIP ya no estaban y **los restauró** (diff idéntico al original), sin tocar la lista ni el umbral. Verify: 53/55 (96 %), *"cero supervivientes sin clasificar"*. Es el resultado esperado de la prueba 1: el loop no deja terminar hasta que los mutantes de la regla nueva están muertos. Lección de proceso: no tocar el árbol con la sesión del agente abierta (aquí reaccionó a un cambio de la persona como si fuera propio).
