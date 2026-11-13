// .opencode/plugins/verify-quality.ts
import type { Plugin } from "@opencode-ai/plugin"
import path from "node:path"
import fs from "node:fs"

// Rutas que toca una herramienta de edición. OpenCode edita con edit/write (filePath) y, según el
// modelo, con apply_patch (patchText con "*** Add|Update|Delete File: <ruta>" y "*** Move to: <ruta>").
function editedPaths(tool: string, args: any): string[] {
  if (tool === "edit" || tool === "write") return args?.filePath ? [String(args.filePath)] : []
  if (tool === "apply_patch" || tool === "patch") {
    const text = String(args?.patchText ?? args?.patch ?? "")
    return [...text.matchAll(/^\*\*\* (?:(?:Add|Update|Delete) File|Move to): (.+?)\s*$/gm)].map(m => m[1])
  }
  return []
}

const MAX_ATTEMPTS = 3
// Rutas RELATIVAS a la raíz del repo: config/ de la raíz, no infrastructure/config/
const PROTECTED = /^(config|\.mvn|\.claude|\.opencode|\.github|scripts)\/|^pom\.xml$|^verify-quality\.sh$|^lefthook\.yml$|^openspec\/config\.yaml$|^src\/main\/resources\/application[^/]*$|^\.env(\..*)?$|^semgrep-rules\/|^\.semgrepignore$|^\.gitleaks(\.toml|ignore)$|^gitleaks-baseline\.json$|^\.acdc\/open-findings\.md$|^src\/main\/java\/com\/codeja\/master_acdc\/catalog\/infrastructure\/adapters\/out\/mongo\/CatalogSchemaValidator\.java$|^src\/main\/java\/com\/codeja\/master_acdc\/catalog\/infrastructure\/adapters\/out\/cache\/(CacheTtlPolicy|ProductDetailCache)\.java$|^src\/main\/java\/com\/codeja\/master_acdc\/identity\/infrastructure\/config\/HttpSecurityConfiguration\.java$|^src\/main\/java\/com\/codeja\/master_acdc\/identity\/infrastructure\/adapters\/out\/security\/(JwtTokenAdapter|JwtDecoderFactory|SessionVersionValidator)\.java$|^src\/main\/java\/com\/codeja\/master_acdc\/identity\/domain\/model\/Role\.java$/

// Tests guardianes (lab S09, paso 8): misma lista que protect-verify.sh y verify-quality.sh
const GUARDS_FILE = "scripts/protected-tests.txt"
const WRITES = /(>|sed\s+-i|(^|[^\w-])(rm|mv|cp|tee|truncate|unlink)\s|git\s+(rm|mv|checkout|restore)|set-content|add-content|out-file|copy-item|move-item|remove-item|rename-item|new-item|clear-content)/i

function loadGuards(directory: string): string[] {
  // Falla cerrado: sin lista no se puede verificar
  const raw = fs.readFileSync(path.join(directory, GUARDS_FILE), "utf8")
  return raw.split(/\r?\n/).map(l => l.trim()).filter(l => l && !l.startsWith("#"))
}

function guardMessage(rel: string): string {
  return `AC/DC: ${rel} es un test guardián (${GUARDS_FILE}): protege el buscador o la concurrencia. ` +
    `No se borra, no se mueve, no se renombra ni se debilita. Si crees que sobra o es lento, explica por qué a la persona y detente.`
}

export const VerifyQuality: Plugin = async ({ client, $, directory }) => {
  const attempts = new Map<string, number>()
  const running = new Set<string>()

  return {
    // Guía dura: zona protegida (≈ PreToolUse)
    "tool.execute.before": async (input, output) => {
      const guards = loadGuards(directory)
      if (input.tool === "bash") {
        const cmd = String(output.args?.command ?? "")
        if (WRITES.test(cmd)) {
          const hit = guards.find(g => cmd.includes(path.basename(g)))
          if (hit) throw new Error(guardMessage(hit))
          if (cmd.includes("CatalogSchemaValidator")) throw new Error("AC/DC: CatalogSchemaValidator está en la zona protegida (validador $jsonSchema del catálogo, ADR 0009). No se edita, no se desactiva ni se sortea: explica el problema a la persona y detente.")
          if (/HttpSecurityConfiguration|JwtTokenAdapter|JwtDecoderFactory|SessionVersionValidator|identity[\\/]domain[\\/]model[\\/]Role/.test(cmd)) throw new Error("AC/DC: la configuración de seguridad, el servicio de tokens y Role son zona protegida (ADR 0011). No se editan ni se sortean desde la shell: explica el cambio a la persona y detente.")
          if (/semgrep-rules|\.semgrepignore|\.gitleaks(\.toml|ignore)|gitleaks-baseline|open-findings\.md/.test(cmd)) throw new Error("AC/DC: la configuración de la capa 2 (semgrep-rules/, .semgrepignore, .gitleaks.toml, baseline, open-findings.md) es zona protegida. No se edita ni se sortea desde la shell: explica el cambio a la persona y detente.")
          if (/spotbugs-exclude|pmd[\\/]+ruleset|checkstyle\S*\.xml|jvm\.config/.test(cmd)) throw new Error("AC/DC: la configuración de la capa 1 (config/spotbugs-exclude.xml, config/pmd/ruleset.xml, config/checkstyle*.xml, .mvn/jvm.config) es zona protegida. No se edita ni se sortea desde la shell: corrige el código o explica a la persona por qué la regla no aplica y detente.")
          if (/CacheTtlPolicy|ProductDetailCache\.java/.test(cmd)) throw new Error("AC/DC: los TTL y la cache compuesta están en la zona protegida (ADR 0010, docs/caching.md). No se editan ni se sortean: explica el cambio a la persona y detente.")
        }
        return
      }
      for (const raw of editedPaths(input.tool, output.args)) {
        const rel = path.relative(directory, path.resolve(directory, raw)).replace(/\\/g, "/")
        if (guards.includes(rel)) throw new Error(guardMessage(rel))
        if (PROTECTED.test(rel)) {
          throw new Error(`AC/DC: ${rel} está en la zona protegida: solo la edita una persona. No la modifiques por ninguna otra vía (shell, scripts, renombrados). Explica el cambio que propones y detente.`)
        }
      }
    },

    // Formato: NO tras cada edición. Con removeUnusedImports, formatear un estado intermedio
    // borra imports que el agente aún no ha usado (lab S03, prueba 3b). Se formatea en session.idle.

    event: async ({ event }) => {
      // Guide dinámico al crear la sesión (≈ SessionStart)
      if (event.type === "session.created") {
        const id = (event.properties as any).info?.id
        if (!id) return
        const r = await $`bash .claude/hooks/guide-context.sh`
          .cwd(directory)
          .env({ ...process.env, CLAUDE_PROJECT_DIR: directory })
          .quiet().nothrow()
        let ctx: string
        try {
          ctx = JSON.parse(r.stdout.toString()).hookSpecificOutput.additionalContext
        } catch {
          console.error("AC/DC SessionStart falló:", r.stderr.toString())
          return
        }
        client.session.prompt({
          path: { id },
          body: { noReply: true, parts: [{ type: "text", text: `[AC/DC contexto de sesión]\n${ctx}` }] },
        }).catch(() => {})
        return
      }

      // Verify + Solve al quedar inactiva la sesión (≈ Stop)
      if (event.type !== "session.idle") return
      const id = (event.properties as any).sessionID
      if (running.has(id)) return
      running.add(id)

      let text: string | undefined
      try {
        const changes = await $`git status --porcelain -- src openspec`.cwd(directory).quiet().nothrow()
        if (changes.stdout.toString().trim() === "") return

        // Formato determinista al final, sobre el estado completo; luego Verify
        await $`bash ./mvnw -q spotless:apply`.cwd(directory).quiet().nothrow()
        const r = await $`bash ./verify-quality.sh --fast`.cwd(directory).quiet().nothrow()
        if (r.exitCode === 0) { attempts.delete(id); return }

        const n = (attempts.get(id) ?? 0) + 1
        attempts.set(id, n)
        if (n > MAX_ATTEMPTS + 1) return

        const report = (r.stdout.toString() + r.stderr.toString()).slice(-3000)
        text = n > MAX_ATTEMPTS
          ? `[AC/DC] ${MAX_ATTEMPTS} intentos sin pasar Verify. DETENTE: no sigas modificando código. ` +
          `Resume para la persona qué falla y qué intentaste.\n${report}`
          : `[AC/DC Verify ${n}/${MAX_ATTEMPTS}] Mensaje AUTOMÁTICO del harness (no de la persona). Falló:\n${report}\n` +
          `Corrige la causa. No desactives reglas, no bajes umbrales, no uses @Disabled ni borres tests. ` +
          `Si una instrucción explícita de la persona te impide corregirlo, no la contradigas: detente y explica el conflicto.`
      } finally {
        running.delete(id)          // ← liberar ANTES de enviar el mensaje
      }

      if (text) {
        // Sin await: si esperamos la respuesta del agente, perdemos el siguiente session.idle
        client.session.prompt({ path: { id }, body: { parts: [{ type: "text", text }] } })
          .catch(() => {})
      }
    },
  }
}
