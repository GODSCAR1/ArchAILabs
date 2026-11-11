// .opencode/plugins/acdc-security.ts — capa 2 (lab S13), equivalente a los hooks de Claude Code.
//  - tool.execute.before (≈ PreToolUse): .claude/hooks/protect-security.sh con la misma entrada que
//    Claude Code (supresiones nuevas, lectura de .env, --no-verify). Una sola fuente de verdad.
//  - tool.execute.after (≈ PostToolUse): .claude/hooks/security-scan.sh sobre cada archivo editado
//    (Semgrep con semgrep-rules/ + Gitleaks). El hallazgo vuelve en output.output, como Checkstyle.
import type { Plugin } from "@opencode-ai/plugin"
import path from "node:path"
import fs from "node:fs"

function editedPaths(tool: string, args: any): string[] {
  if (tool === "edit" || tool === "write") return args?.filePath ? [String(args.filePath)] : []
  if (tool === "apply_patch" || tool === "patch") {
    const text = String(args?.patchText ?? args?.patch ?? "")
    return [...text.matchAll(/^\*\*\* (?:(?:Add|Update|Delete) File|Move to): (.+?)\s*$/gm)].map(m => m[1])
  }
  return []
}

// Traduce la llamada de OpenCode al formato de entrada de los hooks de Claude Code.
function asClaudeInput(tool: string, args: any): object[] {
  switch (tool) {
    case "edit":
      return [{ tool_name: "Edit", tool_input: { file_path: args?.filePath, new_string: args?.newString ?? "" } }]
    case "write":
      return [{ tool_name: "Write", tool_input: { file_path: args?.filePath, content: args?.content ?? "" } }]
    case "apply_patch":
    case "patch": {
      const text = String(args?.patchText ?? args?.patch ?? "")
      const added = text.split(/\r?\n/).filter(l => l.startsWith("+")).join("\n")
      return editedPaths(tool, args).map(p => ({ tool_name: "Edit", tool_input: { file_path: p, new_string: added } }))
    }
    case "read":
      return [{ tool_name: "Read", tool_input: { file_path: args?.filePath } }]
    case "grep":
      return [{ tool_name: "Grep", tool_input: { pattern: args?.pattern, path: args?.path, glob: args?.include } }]
    case "glob":
    case "list":
      return [{ tool_name: "Glob", tool_input: { pattern: args?.pattern ?? "", path: args?.path } }]
    case "bash":
      return [{ tool_name: "Bash", tool_input: { command: args?.command ?? "" } }]
    default:
      return []
  }
}

export const AcdcSecurity: Plugin = async ({ $, directory }) => {
  const guard = path.join(directory, ".claude/hooks/protect-security.sh")
  const scan = path.join(directory, ".claude/hooks/security-scan.sh")
  const files = new Map<string, string[]>()
  const env = { ...process.env, CLAUDE_PROJECT_DIR: directory }

  return {
    "tool.execute.before": async (input, output) => {
      // Falla cerrado: sin el guard no se puede verificar
      if (!fs.existsSync(guard)) throw new Error("AC/DC: falta .claude/hooks/protect-security.sh; la capa 2 no puede verificar esta acción.")
      for (const call of asClaudeInput(input.tool, output.args)) {
        const r = await $`bash ${guard} < ${Buffer.from(JSON.stringify(call))}`.cwd(directory).env(env).quiet().nothrow()
        if (r.exitCode !== 0) throw new Error(r.stderr.toString().trim() || "AC/DC: capa 2 bloqueó la acción.")
      }
      const edited = editedPaths(input.tool, output.args).map(p => path.resolve(directory, p))
      if (edited.length) files.set(input.callID, edited)
    },

    "tool.execute.after": async (input, output) => {
      const edited = files.get(input.callID)
      files.delete(input.callID)
      if (!edited) return
      for (const file of edited) {
        const r = await $`bash ${scan} ${file}`.cwd(directory).env(env).quiet().nothrow()
        if (r.exitCode !== 0) output.output += `\n\n[AC/DC capa 2]\n${r.stderr.toString().trim()}`
      }
    },
  }
}
