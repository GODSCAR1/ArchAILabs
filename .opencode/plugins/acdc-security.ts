// .opencode/plugins/acdc-security.ts — capa 2 (lab S13), equivalente a los hooks de Claude Code.
//  - execute.before (≈ PreToolUse): .claude/hooks/protect-security.sh con la misma entrada que
//    Claude Code (supresiones nuevas, lectura de .env, --no-verify). Una sola fuente de verdad.
//  - execute.after (≈ PostToolUse): .claude/hooks/security-scan.sh sobre cada archivo editado
//    (Semgrep con semgrep-rules/ + Gitleaks). El hallazgo se añade al resultado, como Checkstyle.
// Lab S16: migrado a la API V2 de OpenCode (ver .opencode/lib/acdc.ts).
import path from "node:path"
import fs from "node:fs"
import { appendToResult, editedPaths, isShell, heartbeat, run } from "../lib/acdc.ts"

// Traduce la llamada de OpenCode al formato de entrada de los hooks de Claude Code.
function asClaudeInput(tool: string, input: any): object[] {
  const file = input?.path ?? input?.filePath ?? input?.file_path
  if (isShell(tool)) return [{ tool_name: "Bash", tool_input: { command: input?.command ?? "" } }]
  switch (tool) {
    case "edit":
      return [{ tool_name: "Edit", tool_input: { file_path: file, new_string: input?.newString ?? input?.new_string ?? input?.newText ?? "" } }]
    case "write":
      return [{ tool_name: "Write", tool_input: { file_path: file, content: input?.content ?? "" } }]
    case "apply_patch":
    case "patch": {
      const text = String(input?.patchText ?? input?.patch ?? "")
      const added = text.split(/\r?\n/).filter(l => l.startsWith("+")).join("\n")
      return editedPaths(tool, input).map(p => ({ tool_name: "Edit", tool_input: { file_path: p, new_string: added } }))
    }
    case "read":
      return [{ tool_name: "Read", tool_input: { file_path: file } }]
    case "grep":
      return [{ tool_name: "Grep", tool_input: { pattern: input?.pattern, path: input?.path, glob: input?.include ?? input?.glob } }]
    case "glob":
    case "list":
      return [{ tool_name: "Glob", tool_input: { pattern: input?.pattern ?? "", path: input?.path } }]
    default:
      return []
  }
}

export default {
  id: "acdc.security",
  async setup(ctx: any) {
    await heartbeat(ctx, "acdc.security")
    const directory: string = ctx.location.directory
    const guard = path.join(directory, ".claude/hooks/protect-security.sh")
    const scan = path.join(directory, ".claude/hooks/security-scan.sh")
    const env = { ...process.env, CLAUDE_PROJECT_DIR: directory }

    await ctx.tool.hook("execute.before", async (event: any) => {
      // Falla cerrado: sin el guard no se puede verificar
      if (!fs.existsSync(guard)) throw new Error("AC/DC: falta .claude/hooks/protect-security.sh; la capa 2 no puede verificar esta acción.")
      for (const call of asClaudeInput(event.tool, event.input)) {
        const r = await run("bash", [guard], { cwd: directory, env, input: JSON.stringify(call) })
        if (r.code !== 0) throw new Error(r.stderr.trim() || "AC/DC: capa 2 bloqueó la acción.")
      }
    })

    await ctx.tool.hook("execute.after", async (event: any) => {
      if (event.status !== "completed") return
      for (const file of editedPaths(event.tool, event.input).map(p => path.resolve(directory, p))) {
        if (!fs.existsSync(file)) continue          // borrado por el parche
        const r = await run("bash", [scan, file], { cwd: directory, env })
        if (r.code !== 0) appendToResult(event, `\n\n[AC/DC capa 2]\n${r.stderr.trim()}`)
      }
    })
  },
}
