// .opencode/plugins/verify-quality.ts — guía dura (zona protegida, ≈ PreToolUse), contexto de sesión
// (≈ SessionStart) y Verify al terminar el turno (≈ Stop).
// Lab S16: migrado a la API V2 de OpenCode (ver .opencode/lib/acdc.ts). Dos diferencias con V1:
//  - El contexto de sesión ya no es un mensaje: va en las instrucciones de sistema de cada petición
//    (ctx.session.hook("context")), así que el agente lo tiene siempre, no solo al empezar.
//  - V2 no deja escribir mensajes en la sesión: si Verify falla al terminar el turno, el informe se
//    añade a las instrucciones de las siguientes peticiones hasta que Verify pase. El agente lo verá en
//    su siguiente turno (ya no arranca solo a corregir, como en V1).
import path from "node:path"
import fs from "node:fs"
import { editedPaths, isShell, run, runSync, subscribe } from "../lib/acdc.ts"

const MAX_ATTEMPTS = 3
// Rutas RELATIVAS a la raíz del repo: config/ de la raíz, no infrastructure/config/
const PROTECTED = /^(config|\.mvn|\.claude|\.opencode|\.github|scripts)\/|^pom\.xml$|^verify-quality\.sh$|^lefthook\.yml$|^openspec\/config\.yaml$|^src\/main\/resources\/application[^/]*$|^\.env(\..*)?$|^semgrep-rules\/|^\.semgrepignore$|^\.gitleaks(\.toml|ignore)$|^gitleaks-baseline\.json$|^\.acdc\/open-findings\.md$|^src\/main\/java\/com\/codeja\/master_acdc\/catalog\/infrastructure\/adapters\/out\/mongo\/CatalogSchemaValidator\.java$|^src\/main\/java\/com\/codeja\/master_acdc\/catalog\/infrastructure\/adapters\/out\/cache\/(CacheTtlPolicy|ProductDetailCache)\.java$|^src\/main\/java\/com\/codeja\/master_acdc\/identity\/infrastructure\/config\/HttpSecurityConfiguration\.java$|^src\/main\/java\/com\/codeja\/master_acdc\/identity\/infrastructure\/adapters\/out\/security\/(JwtTokenAdapter|JwtDecoderFactory|SessionVersionValidator)\.java$|^src\/main\/java\/com\/codeja\/master_acdc\/identity\/domain\/model\/Role\.java$/

// Tests guardianes (lab S09, paso 8): misma lista que protect-verify.sh y verify-quality.sh
const GUARDS_FILE = "scripts/protected-tests.txt"
const WRITES = /(>|sed\s+-i|perl\s+-\w*i|(^|[^\w-])(rm|mv|cp|tee|truncate|unlink)\s|git\s+(rm|mv|checkout|restore)|set-content|add-content|out-file|copy-item|move-item|remove-item|rename-item|new-item|clear-content)/i

function loadGuards(directory: string): string[] {
  // Falla cerrado: sin lista no se puede verificar
  const raw = fs.readFileSync(path.join(directory, GUARDS_FILE), "utf8")
  return raw.split(/\r?\n/).map(l => l.trim()).filter(l => l && !l.startsWith("#"))
}

function guardMessage(rel: string): string {
  return `AC/DC: ${rel} es un test guardián (${GUARDS_FILE}): protege el buscador o la concurrencia. ` +
    `No se borra, no se mueve, no se renombra ni se debilita. Si crees que sobra o es lento, explica por qué a la persona y detente.`
}

function checkShell(cmd: string, guards: string[]): void {
  // Lab S14: el umbral de cobertura no se sortea pasando la propiedad (-D, MAVEN_OPTS) a Maven.
  if (/acdc\.coverage/.test(cmd)) throw new Error("AC/DC: el umbral de cobertura de ramas (acdc.coverage.* en pom.xml) no se cambia ni se desactiva desde la línea de comandos. Cubre las ramas de regla con tests o explica a la persona por qué una rama no merece test y detente.")
  // Lab S15: ni el umbral de PIT ni sus mutadores por la línea de comandos; nadie antepone otro PATH a verify-quality.sh.
  if (/mutationThreshold|-Dmutators/.test(cmd)) throw new Error("AC/DC: el umbral de mutation testing (pit.mutationThreshold en pom.xml) y los mutadores no se cambian desde la línea de comandos. Mide con la execution pit-pricing; si un superviviente no es una regla, clasifícalo en docs/lab-s15.md y detente.")
  if (/(^|[^\w])PATH=.*verify-quality\.sh/.test(cmd)) throw new Error("AC/DC: verify-quality.sh se ejecuta con el PATH de la persona. Si una herramienta (semgrep, gitleaks) no arranca, explícalo a la persona y detente; no la sustituyas.")
  if (WRITES.test(cmd)) {
    const hit = guards.find(g => cmd.includes(path.basename(g)))
    if (hit) throw new Error(guardMessage(hit))
    if (cmd.includes("CatalogSchemaValidator")) throw new Error("AC/DC: CatalogSchemaValidator está en la zona protegida (validador $jsonSchema del catálogo, ADR 0009). No se edita, no se desactiva ni se sortea: explica el problema a la persona y detente.")
    if (/HttpSecurityConfiguration|JwtTokenAdapter|JwtDecoderFactory|SessionVersionValidator|identity[\\/]domain[\\/]model[\\/]Role/.test(cmd)) throw new Error("AC/DC: la configuración de seguridad, el servicio de tokens y Role son zona protegida (ADR 0011). No se editan ni se sortean desde la shell: explica el cambio a la persona y detente.")
    if (/semgrep-rules|\.semgrepignore|\.gitleaks(\.toml|ignore)|gitleaks-baseline|open-findings\.md/.test(cmd)) throw new Error("AC/DC: la configuración de la capa 2 (semgrep-rules/, .semgrepignore, .gitleaks.toml, baseline, open-findings.md) es zona protegida. No se edita ni se sortea desde la shell: explica el cambio a la persona y detente.")
    if (/spotbugs-exclude|pmd[\\/]+ruleset|checkstyle\S*\.xml|jvm\.config/.test(cmd)) throw new Error("AC/DC: la configuración de la capa 1 (config/spotbugs-exclude.xml, config/pmd/ruleset.xml, config/checkstyle*.xml, .mvn/jvm.config) es zona protegida. No se edita ni se sortea desde la shell: corrige el código o explica a la persona por qué la regla no aplica y detente.")
    // Lab S14 (prueba 4): el build y el loop (umbral de JaCoCo, gate de verify-quality.sh) tampoco se editan desde la shell.
    // Lab S15 (prueba 2): el gate de la capa 3 y la lista de supervivientes aceptados viven en scripts/.
    if (/(^|[^\w.-])scripts[\\/]/.test(cmd)) throw new Error("AC/DC: scripts/ es zona protegida (gates de Verify: mutation-gate.sh, pit-accepted-survivors.txt, tests guardianes, supresiones). No se edita ni se sortea desde la shell: explica el cambio a la persona y detente.")
    if (/(^|[^\w.-])(pom\.xml|verify-quality\.sh|lefthook\.yml)|\.mvn[\\/]/.test(cmd)) throw new Error("AC/DC: pom.xml, verify-quality.sh, lefthook.yml y .mvn/ son zona protegida (incluido el umbral de JaCoCo). No se editan ni se sortean desde la shell: explica el cambio a la persona y detente.")
    if (/CacheTtlPolicy|ProductDetailCache\.java/.test(cmd)) throw new Error("AC/DC: los TTL y la cache compuesta están en la zona protegida (ADR 0010, docs/caching.md). No se editan ni se sortean: explica el cambio a la persona y detente.")
    // Lab S16: las migraciones no se escriben desde la shell (las nuevas, con la herramienta de edición).
    if (/db[\\/]+migration/.test(cmd)) throw new Error("AC/DC: las migraciones de Flyway no se escriben, mueven ni borran desde la shell. Crea las nuevas con la herramienta de edición; las existentes no se tocan (expandir-contraer).")
  }
}

function checkEdit(directory: string, raw: string, guards: string[]): void {
  const rel = path.relative(directory, path.resolve(directory, raw)).replace(/\\/g, "/")
  if (guards.includes(rel)) throw new Error(guardMessage(rel))
  // Lab S16: una migración que ya existe no se edita (Flyway guarda su checksum); el cambio va en una nueva.
  if (rel.startsWith("src/main/resources/db/migration/") && fs.existsSync(path.join(directory, rel))) {
    throw new Error(`AC/DC: ${rel} es una migración existente: no se edita. Expresa el cambio en una migración nueva (expandir-contraer: añadir, migrar datos y, en otra versión, retirar).`)
  }
  if (PROTECTED.test(rel)) {
    throw new Error(`AC/DC: ${rel} está en la zona protegida: solo la edita una persona. No la modifiques por ninguna otra vía (shell, scripts, renombrados). Explica el cambio que propones y detente.`)
  }
}

export default {
  id: "acdc.verify-quality",
  async setup(ctx: any) {
    const directory: string = ctx.location.directory
    const env = { ...process.env, CLAUDE_PROJECT_DIR: directory }
    const attempts = new Map<string, number>()
    const running = new Set<string>()
    const guide = new Map<string, string>()    // contexto de sesión, calculado una vez por sesión
    const pending = new Map<string, string>()  // informe del último Verify fallido de cada sesión

    // Guía dura: zona protegida (≈ PreToolUse)
    await ctx.tool.hook("execute.before", (event: any) => {
      const guards = loadGuards(directory)
      if (isShell(event.tool)) {
        checkShell(String(event.input?.command ?? ""), guards)
        return
      }
      for (const raw of editedPaths(event.tool, event.input)) checkEdit(directory, raw, guards)
    })

    // Guía dinámica (≈ SessionStart) y feedback de Verify en cada petición al modelo.
    // Síncrono a propósito: el contexto tiene que estar en la PRIMERA petición de la sesión.
    await ctx.session.hook("context", (event: any) => {
      const id: string = event.sessionID
      if (!guide.has(id)) guide.set(id, guideContext())
      const text = guide.get(id)
      if (text) event.system.push({ type: "text", text: `[AC/DC contexto de sesión]\n${text}` })
      const report = pending.get(id)
      if (report) event.system.push({ type: "text", text: report })
    })

    // Verify + Solve al terminar el turno (≈ Stop)
    return subscribe(ctx, (event: any) => {
      if (event?.type === "session.execution.succeeded" && event.data?.sessionID) return verify(event.data.sessionID)
    })

    function guideContext(): string {
      const r = runSync("bash", [".claude/hooks/guide-context.sh"], { cwd: directory, env })
      try {
        return JSON.parse(r.stdout).hookSpecificOutput.additionalContext
      } catch {
        console.error("AC/DC SessionStart falló:", r.stderr)
        return ""
      }
    }

    async function verify(id: string): Promise<void> {
      if (running.has(id)) return
      running.add(id)
      try {
        const changes = await run("git", ["status", "--porcelain", "--", "src", "openspec"], { cwd: directory })
        if (changes.stdout.trim() === "") return

        // Formato determinista al final, sobre el estado completo; luego Verify
        await run("bash", ["./mvnw", "-q", "spotless:apply"], { cwd: directory })
        const r = await run("bash", ["./verify-quality.sh", "--fast"], { cwd: directory })
        if (r.code === 0) {
          attempts.delete(id)
          pending.delete(id)
          return
        }
        const n = (attempts.get(id) ?? 0) + 1
        attempts.set(id, n)
        const report = (r.stdout + r.stderr).slice(-3000)
        pending.set(id, n > MAX_ATTEMPTS
          ? `[AC/DC] ${MAX_ATTEMPTS} intentos sin pasar Verify. DETENTE: no sigas modificando código. ` +
            `Resume para la persona qué falla y qué intentaste.\n${report}`
          : `[AC/DC Verify ${n}/${MAX_ATTEMPTS}] Mensaje AUTOMÁTICO del harness (no de la persona): el Verify ` +
            `que se ejecutó al terminar tu último turno falló.\n${report}\n` +
            `Corrige la causa antes de seguir. No desactives reglas, no bajes umbrales, no uses @Disabled ni borres tests. ` +
            `Si una instrucción explícita de la persona te impide corregirlo, no la contradigas: detente y explica el conflicto.`)
      } finally {
        running.delete(id)
      }
    }
  },
}
