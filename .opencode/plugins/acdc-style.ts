// .opencode/plugins/acdc-style.ts — capa 1 por archivo (≈ PostToolUse): Checkstyle, compilación
// (Error Prone + NullAway), SpotBugs/FindSecBugs y PMD del .java editado.
// Un solo Verify: ejecuta el MISMO script que el hook de Claude Code (.claude/hooks/verify-file.sh).
// El feedback se añade al resultado de la herramienta: el agente lo lee junto a su propia edición.
// El formato NO se aplica aquí (removeUnusedImports rompe ediciones en varios pasos, lab S03):
// Spotless corre al terminar el turno (verify-quality.ts) y en el pre-commit.
// Lab S16: migrado a la API V2 de OpenCode (ver .opencode/lib/acdc.ts).
import path from "node:path"
import { appendToResult, editedPaths, heartbeat, run } from "../lib/acdc.ts"

export default {
  id: "acdc.style",
  async setup(ctx: any) {
    await heartbeat(ctx, "acdc.style")
    const directory: string = ctx.location.directory

    await ctx.tool.hook("execute.after", async (event: any) => {
      if (event.status !== "completed") return
      const java = editedPaths(event.tool, event.input)
        .filter(file => file.endsWith(".java"))
        .map(file => path.resolve(directory, file))
      if (!java.length) return
      const report = await verifyFiles(java)
      if (report) appendToResult(event, report)
    })

    async function verifyFiles(files: string[]): Promise<string> {
      // Raíz del árbol de los ARCHIVOS (worktrees), no el directorio de la sesión
      const top = await run("git", ["-C", path.dirname(files[0]), "rev-parse", "--show-toplevel"], { cwd: directory })
      if (top.code !== 0) return ""
      const root = path.resolve(top.stdout.trim())
      const sources = files.filter(file =>
        /^src\/(main|test)\/java\//.test(path.relative(root, file).replace(/\\/g, "/")))
      if (!sources.length) return ""
      // Todas las rutas en UNA llamada: una pasada de Maven por paso, no una por archivo.
      // El script devuelve exit 2 y el informe por stderr si algo falla.
      const r = await run("bash", [".claude/hooks/verify-file.sh", ...sources], { cwd: root })
      if (r.code === 0) return ""
      const report = r.stderr.trim() || (r.stdout + r.stderr).slice(-1500)
      return `\n\n[AC/DC capa 1] ${report}`
    }
  },
}
