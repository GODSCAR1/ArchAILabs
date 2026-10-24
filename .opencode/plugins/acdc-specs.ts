// .opencode/plugins/acdc-specs.ts — specs aprobadas protegidas + validación del change editado.
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

// Rutas normalizadas a "/": el agente usa PowerShell y rutas de Windows
const PROTECTED = /openspec[\\/](specs|changes[\\/]archive)([\\/]|\s|$)/
const WRITES = /(>|sed -i|rm |mv |cp |tee |set-content|add-content|out-file|copy-item|move-item|remove-item|new-item)/i

export const AcdcSpecs: Plugin = async ({ $, directory }) => {
  const files = new Map<string, string>()
  return {
    "tool.execute.before": async (input, output) => {
      if (input.tool === "bash") {
        const cmd: string = String(output.args?.command ?? "")
        if (/\bopenspec\s+archive\b/i.test(cmd))
          throw new Error("AC/DC: archivar un change lo hace una persona tras revisar el PR. Termina las tareas y avisa.")
        if (PROTECTED.test(cmd) && WRITES.test(cmd))
          throw new Error("AC/DC: openspec/specs y el archivo de changes están protegidos.")
        return
      }
      for (const raw of editedPaths(input.tool, output.args)) {
        const file = path.resolve(directory, raw).replace(/\\/g, "/")
        if (/\/openspec\/(specs|changes\/archive)\//.test(file))
          throw new Error(`AC/DC: ${file} es una spec aprobada. No la edites: crea o actualiza un change en openspec/changes/<nombre>/ con un delta.`)
        files.set(input.callID, file)   // valida el último change editado (como antes, uno por llamada)
      }
    },
    "tool.execute.after": async (input, output) => {
      const file = files.get(input.callID)
      files.delete(input.callID)
      const change = file?.match(/\/openspec\/changes\/([^/]+)\//)?.[1]
      if (!file || !change || change === "archive" || !/^[A-Za-z0-9._-]+$/.test(change)) return
      const root = file.split("/openspec/changes/")[0]   // raíz del árbol editado (worktrees)
      const r = await $`bash -c ${`OPENSPEC_TELEMETRY=0 openspec validate ${change} --strict --no-interactive`}`
        .cwd(root).quiet().nothrow()
      if (r.exitCode !== 0) {
        const report = r.stderr.toString() + r.stdout.toString()
        const issues = report.split("\n").filter(l => /\[(ERROR|WARNING|INFO)\]/.test(l)).slice(0, 10).join("\n")
        output.output += `\n\n[AC/DC Verify] El change '${change}' no es válido. Corrige:\n${issues || report.slice(0, 1500)}`
      }
    },
  }
}
