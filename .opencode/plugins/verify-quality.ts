// .opencode/plugins/verify-quality.ts
import type { Plugin } from "@opencode-ai/plugin"
import path from "node:path"

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
const PROTECTED = /^(config|\.claude|\.opencode|\.github|scripts)\/|^pom\.xml$|^verify-quality\.sh$|^lefthook\.yml$|^openspec\/config\.yaml$|^src\/main\/resources\/application[^/]*$|^\.env(\..*)?$/

export const VerifyQuality: Plugin = async ({ client, $, directory }) => {
  const attempts = new Map<string, number>()
  const running = new Set<string>()

  return {
    // Guía dura: zona protegida (≈ PreToolUse)
    "tool.execute.before": async (input, output) => {
      for (const raw of editedPaths(input.tool, output.args)) {
        const rel = path.relative(directory, path.resolve(directory, raw)).replace(/\\/g, "/")
        if (PROTECTED.test(rel)) {
          throw new Error(`AC/DC: ${rel} está en la zona protegida. Pide permiso a un humano.`)
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
