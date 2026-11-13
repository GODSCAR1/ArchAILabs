// .opencode/plugins/acdc-style.ts — capa 1 por archivo (≈ PostToolUse): Checkstyle, compilación
// (Error Prone + NullAway), SpotBugs/FindSecBugs y PMD del .java editado.
// Un solo Verify: ejecuta el MISMO script que el hook de Claude Code (.claude/hooks/verify-file.sh).
// El feedback va en output.output: el agente lo lee como parte del resultado de su propia edición.
// El formato NO se aplica aquí (removeUnusedImports rompe ediciones en varios pasos, lab S03):
// Spotless corre en session.idle (verify-quality.ts) y en el pre-commit.
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

export const AcdcStyle: Plugin = async ({ $, directory }) => {
  // tool.execute.after no siempre trae los argumentos: la ruta se guarda en before, por callID
  const files = new Map<string, string[]>()

  return {
    "tool.execute.before": async (input, output) => {
      const java = editedPaths(input.tool, output.args)
        .filter(p => p.endsWith(".java"))
        .map(p => path.resolve(directory, p))
      if (java.length) files.set(input.callID, java)
    },

    "tool.execute.after": async (input, output) => {
      const edited = files.get(input.callID)
      files.delete(input.callID)                       // sin esto, el Map crece sin límite
      if (!edited) return
      for (const file of edited) output.output += await verifyFile(file)
    },
  }

  async function verifyFile(file: string): Promise<string> {
    // Raíz del árbol del ARCHIVO (worktrees), no el directorio de la sesión
    const top = await $`git -C ${path.dirname(file)} rev-parse --show-toplevel`.quiet().nothrow()
    if (top.exitCode !== 0) return ""
    const root = path.resolve(top.stdout.toString().trim())
    const rel = path.relative(root, file).replace(/\\/g, "/")
    if (!/^src\/(main|test)\/java\//.test(rel)) return ""

    // La ruta va como argumento; el script devuelve exit 2 y el informe por stderr si algo falla
    const r = await $`bash .claude/hooks/verify-file.sh ${file}`.cwd(root).quiet().nothrow()
    if (r.exitCode === 0) return ""
    const report = r.stderr.toString().trim() || (r.stdout.toString() + r.stderr.toString()).slice(-1500)
    return `\n\n[AC/DC capa 1] ${report}`
  }
}
