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
*(pendiente)*

## 5. Prompt
*(pendiente)*

## 6. Implementar y revisar
*(pendiente)*

## 7. ¿Qué decidió el agente que yo no especifiqué? ¿Cómo lo capturo?
*(pendiente — candidatas hasta ahora: resolver el conflicto persona/gate a favor del gate; dar por buena una tarea sin releer el archivo; describir como existente lo que el Guide solo anuncia)*
