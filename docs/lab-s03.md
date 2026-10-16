# Lab S03 — Guiar una tarea de principio a fin

Prompt + contexto persistente + Graphify + gate de calidad sobre el repositorio base (OpenCode).

## 1. Contexto (`AGENTS.md`)
- Recortado con la prueba de borrado: **33 → 38 líneas** tras añadir la sección de Graphify (límite: 40).
- **Eliminadas:** introducción ("léelas antes…"), "no discutas formato" (el formato lo aplica una herramienta), "inyección por constructor" (el agente ya lo hace por defecto), "PIT 75 %" (lo inyecta `guide-context.sh` leyendo el valor real del `pom.xml`), "OWASP para dependencias nuevas" (el `pom.xml` ya está protegido).
- **Añadidas:** sección Comandos; "Boot 4: `@MockitoBean`, no `@MockBean`" (el error que cometería, no la versión); "pega la salida completa" de Verify.
- **Corregidas tras observar al agente:**
  - *"en Windows, desde Git Bash"* → el shell del agente en OpenCode es **PowerShell**: los scripts se invocan con `bash ./…`. Con la regla original habría fallado al verificar.
  - Etiquetas `(ArchUnit desde S17)` → `(pendiente: ArchUnit en S17)` + "no las describas como verificadas". El agente afirmó "reglas verificadas por ArchUnit" leyendo la etiqueta antigua.
- `CLAUDE.md` = `@AGENTS.md` + lo específico de Claude Code (sin cambios).

## 2. Mapa (Graphify)
- `uv tool install graphifyy` · `graphify install --platform opencode` · `/graphify .` · `graphify opencode install` · `graphify hook install`.
- `.graphifyignore`: `target/`, `.idea/`, `.opencode/node_modules/`, `docs/evidencias/`, `mvnw`, `mvnw.cmd`, `session-*.md`. `graphify-out/` añadido a `.gitignore` (no lo estaba).
- **El grafo encontró documentación desactualizada:** conexión sorprendente `com.codeja.master_acdc ~ dev.codeja.shop` → el `README.md` seguía con el nombre antiguo del paquete. Corregido.
- **Graphify escribe en el repo sin avisar:** sección en inglés en `AGENTS.md` (12 líneas; recortada a 3 con la prueba de borrado), `.opencode/plugins/graphify.js` (reescribe el primer comando `bash` de la sesión), `.opencode/opencode.json`, línea `merge=graphify` en `.gitattributes`, hooks `post-commit`/`post-checkout`.
- **Privacidad/coste:** sin clave de Gemini, los `.md` se enviaron al modelo de la sesión (Big Pickle). El informe dice "Token cost: 0", que es falso (el subagente no reporta tokens). Corpus de ~4.200 palabras: el propio Graphify avisa de que "puede que no necesites un grafo".
- **Comprobación:** el agente consultó el grafo antes de leer archivos (`graphify query`), pero:
  - sesión 1: cargó las ~700 líneas de la skill para una pregunta; afirmó clases que no existen (`Product`, `Order`, `Money` — sacadas del javadoc de `package-info`) y "verificado con ArchUnit". → `package-info.java` corregidos (describen responsabilidad, no clases futuras; "aún no activo").
  - sesión 2: fue directo a `query` sin cargar la skill; ya no inventó clases. La consulta en español devolvió **"No matching nodes found"**: los nodos están en inglés → regla añadida "pregunta en inglés".
- Evidencias: `docs/evidencias/graphify-build-opencode.md`, `p2-arquitectura-con-grafo.md`.

## 3. Verify
Diseño: `verify-quality.sh --fast` en `session.idle`, solo si cambió `src/`, reenviando la salida al agente (máx. 3 intentos). Se descarta Verify tras **cada** edición: arranca Maven + Spring (~10-15 s) por edición.
- **Entorno no honesto:** al empezar, Verify fallaba por un `ExternalServiceTest.class` huérfano en `target/` (prueba 3 del lab S02; el `.java` ya no existía). `./mvnw clean` lo resolvió; el CI nunca lo habría visto.
- **3a (error de estilo):** import sin usar + llave mal indentada → `spotless:apply` tras cada edición lo corrigió solo; `git diff` vacío; Verify ni se disparó. Virtud: lo mecánico se corrige mecánicamente. Coste: el agente informó "Hecho: import añadido", **falso**, porque no releyó el archivo.
- **3b (test que falla) — bug encontrado en el inner loop:** el agente añadió el `import static assertEquals` en una edición y el test en la siguiente; `spotless:apply` (con `removeUnusedImports`) se ejecutó entre ambas y **borró el import aún sin usar** → error de compilación. Formatear estados intermedios rompe ediciones en varios pasos.
  - **Corrección:** `spotless:apply` pasa de `tool.execute.after` a `session.idle`, justo antes de Verify. *(Pendiente: el `PostToolUse` de Claude Code — `verify-file.sh` — tiene el mismo problema.)*
- **3b — el gate se impone a la persona:** con "no lo corrijas", el agente se detuvo y preguntó; mientras esperaba, `session.idle` disparó Verify 2/3. El mensaje del plugin llega con rol *User* y el agente lo tomó como una instrucción posterior de la persona → cambió la aserción.
  - **Corrección:** el mensaje dice "Mensaje AUTOMÁTICO del harness (no de la persona)… si una instrucción explícita de la persona te impide corregirlo, detente y explica el conflicto".
  - **Repetición:** la corrección de texto **no bastó**: el agente cambió la aserción a la primera, sin razonar, e informó "verify completó correctamente" sin pegar la salida ni el código de salida. Guía blanda dentro de la guía dura: sigue siendo blanda. (En la repetición el agente hizo import y test en una sola edición, así que el arreglo de Spotless no quedó ejercitado.)
- Evidencias: `docs/evidencias/p3a-p3b-spotless-borra-import.md`, `p3b-repeticion-plugin-corregido.md`.

## 4. Alinear la tarea
Tarea: crear producto (`POST /api/products`). `grill-me` falló al principio: en `.agents/skills` solo hay un stub que delega en `grilling`, y esa skill no estaba instalada. Con `grilling` instalada: **3 rondas, 19 preguntas**.
- **Aprobación sin revisar:** la mayoría de las 19 recomendaciones se aceptaron tal cual. En la revisión A–H, hecha después, cambiaron dos decisiones que habrían llegado al código sin que nadie las eligiera:
  - moneda: EUR con decimales → **COP entero > 0**;
  - tests de integración: siempre → **solo en `--full`**.
- Decisiones en `docs/decisiones/crear-producto.md`. Convención de puertos de entrada (`application/port/<Accion>UseCase` + `<Accion>Service`, adaptadores `in/` y `out/`) añadida a `AGENTS.md`: es lo que el agente habría tenido que adivinar.
- Zona protegida (la edita la persona): `pom.xml` (JPA, validación, `spring-boot-starter-flyway` + `flyway-database-postgresql`, PostgreSQL, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`/`-postgresql` en 2.x, failsafe) y `verify-quality.sh` (`--fast` = `mvn test`, `--full` = `mvn verify`; antes ignoraba el argumento).
- Evidencia: `docs/evidencias/p4-grilling-crear-producto.md`.

## 5. Prompt
`docs/prompt-crear-producto.md`, 5 partes. 8 criterios con un caso a cada lado de cada frontera: sku 2/3 y 64/65, name 120/121, price 0/1. Dos criterios eliminan decisiones silenciosas:
- `1500.5 → 400 (no se trunca)`: Jackson convierte por defecto un decimal a entero (`ACCEPT_FLOAT_AS_INT`) y lo trunca sin error.
- La carrera se prueba de forma determinista en el adaptador, saltándose la comprobación previa.

## 6. Implementar y revisar
- Resultado: 26 tests unitarios y 18 de integración, `--fast` y `--full` en verde (también al ejecutarlos la persona; contrastado con `target/*-reports`). `domain/` y `application/` sin imports de Spring/jakarta. Cada 400/409 comprueba con `count(*)` que no se guardó nada. `MasterAcdcApplicationTests` → `MasterAcdcApplicationIT` (no lo apagó con `exclude`). El gate no tuvo que intervenir.
- **Ejecutar verify uno mismo:** en Git Bash `$LASTEXITCODE` sale vacío (es de PowerShell); en bash es `$?`.
- `/ponytail-review`: `opencode plugin @dietrichgebert/ponytail` instaló el paquete en la caché, pero **la sesión no expuso sus skills**. El agente encontró `SKILL.md` buscando en `~/.cache/opencode/packages` y lo siguió leyéndolo. Instalado ≠ cargado: comprueba que la skill aparece en la sesión antes de confiar en que se aplicó.
- Hallazgos de ponytail (nada que recortar):
  1. *Should fix:* la rama de desbordamiento de `Price.of` (`ArithmeticException` → 400) no tiene test; si alguien borra el `catch`, la respuesta pasa a ser 500 y la suite sigue en verde (y, cuando llegue PIT en S14, será un mutante superviviente). → **aceptado**.
  2. *Nice:* `ProductName` cuenta unidades UTF-16 (un emoji vale 2) mientras PostgreSQL cuenta caracteres → `codePointCount`.
  3. *Nice:* `contextLoads` incumple `should_…_when_…` (lo marcará Checkstyle en S06).
- **Lo que ponytail descartó y yo mantengo:** `ProductRepositoryAdapter` convierte **cualquier** `DataIntegrityViolationException` en `DuplicateSkuException`. Hoy solo existe `uq_products_sku`, así que funciona; con el próximo `NOT NULL`/`CHECK`/FK, ese error saldrá como "SKU duplicado, 409". Ponytail lo vio y lo descartó por especulativo (su regla: "cada hallazgo necesita un caso concreto"). → traducir solo si la restricción violada es `uq_products_sku`.
- Evidencias: `docs/evidencias/p6-implementacion-crear-producto.md`, `p6-ponytail-review.md`.

## 7. ¿Qué decidió el agente que yo no especifiqué? ¿Cómo lo capturo?
| Decisión del agente | ¿Fue correcta? | Cómo capturarla |
|---|---|---|
| "Rojo" = 82 errores de compilación, no aserciones fallidas (vio el problema y lo descartó) | No: un test que no compila no demuestra que compruebe algo | Criterio: "los tests compilan contra stubs vacíos y fallan por aserción" |
| No preguntó nada aunque el prompt decía "pregunta antes de suponer"; tomó 6 decisiones y las listó al final | La lista es honesta, pero llegó después | Guía blanda → no basta. Para decisiones de negocio: dejarlas cerradas en el documento de decisiones antes de lanzar (paso 4), no confiar en que pregunte |
| `Location` relativo, `BigDecimal` en el DTO, `saveAndFlush`, nuevo paquete `domain/exception`, `@RestController` detectado por escaneo y casos de uso con `@Bean`, sin configuración de datasource | Sí, todas razonables | `domain/exception` y lo de escaneo frente a `@Bean` → `AGENTS.md` (son convenciones del repo, se repetirán) |
| "Pega la salida completa" → la recortó con `...` y montó él mismo el resumen de tests | Los números eran ciertos, pero no es evidencia | Verify lo ejecuta la persona (o el CI); el resumen del agente es una afirmación |
| `catch (DataIntegrityViolationException)` amplio | Funciona hoy, falla el día de mañana | Criterio en el siguiente cambio de esquema: "violación de una restricción distinta de `uq_products_sku` → no es 409" |

Además, de los pasos 1–3:
- El gate se impuso a la persona. La corrección por texto no bastó: hace falta un control (pausar el gate mientras el agente espera una respuesta).
- El agente dio una tarea por hecha sin releer el archivo.
- Describió como existente lo que la guía solo anuncia (`package-info`, etiquetas "ArchUnit").

**Patrón común:** todo lo que se pidió como *instrucción* (preguntar, pegar la salida completa, no contradecir a la persona) se cumplió a medias. Lo que se pidió como *criterio verificable* (fronteras, `count(*)`, 1500.5) se cumplió entero. Lo que importa va en criterios o en gates, no en peticiones.
