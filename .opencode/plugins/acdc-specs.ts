// .opencode/plugins/acdc-specs.ts — specs aprobadas protegidas + validación del change editado.
// Lab S16: migrado a la API V2 de OpenCode (ver .opencode/lib/acdc.ts).
import path from "node:path"
import { appendToResult, editedPaths, isShell, run } from "../lib/acdc.ts"

// Rutas normalizadas a "/": el agente puede usar rutas de Windows
const PROTECTED = /openspec[\\/](specs|changes[\\/]archive)([\\/]|\s|$)/
const WRITES = /(>|sed -i|rm |mv |cp |tee |set-content|add-content|out-file|copy-item|move-item|remove-item|new-item)/i

export default {
  id: "acdc.specs",
  async setup(ctx: any) {
    const directory: string = ctx.location.directory

    await ctx.tool.hook("execute.before", (event: any) => {
      if (isShell(event.tool)) {
        const cmd = String(event.input?.command ?? "")
        if (/\bopenspec\s+archive\b/i.test(cmd))
          throw new Error("AC/DC: archivar un change lo hace una persona tras revisar el PR. Termina las tareas y avisa.")
        if (PROTECTED.test(cmd) && WRITES.test(cmd))
          throw new Error("AC/DC: openspec/specs y el archivo de changes están protegidos.")
        return
      }
      for (const raw of editedPaths(event.tool, event.input)) {
        const file = path.resolve(directory, raw).replace(/\\/g, "/")
        if (/\/openspec\/(specs|changes\/archive)\//.test(file))
          throw new Error(`AC/DC: ${file} es una spec aprobada. No la edites: crea o actualiza un change en openspec/changes/<nombre>/ con un delta.`)
      }
    })

    await ctx.tool.hook("execute.after", async (event: any) => {
      if (event.status !== "completed") return
      // Valida el último change editado de la llamada (como antes, uno por llamada)
      const file = editedPaths(event.tool, event.input)
        .map(raw => path.resolve(directory, raw).replace(/\\/g, "/"))
        .filter(f => /\/openspec\/changes\/[^/]+\//.test(f))
        .pop()
      const change = file?.match(/\/openspec\/changes\/([^/]+)\//)?.[1]
      if (!file || !change || change === "archive" || !/^[A-Za-z0-9._-]+$/.test(change)) return
      const root = file.split("/openspec/changes/")[0]   // raíz del árbol editado (worktrees)
      const r = await run("bash", ["-c", `OPENSPEC_TELEMETRY=0 openspec validate ${change} --strict --no-interactive`], { cwd: root })
      if (r.code === 0) return
      const report = r.stderr + r.stdout
      const issues = report.split("\n").filter(l => /\[(ERROR|WARNING|INFO)\]/.test(l)).slice(0, 10).join("\n")
      appendToResult(event, `\n\n[AC/DC Verify] El change '${change}' no es válido. Corrige:\n${issues || report.slice(0, 1500)}`)
    })
  },
}
