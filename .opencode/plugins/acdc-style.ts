// .opencode/plugins/acdc-style.ts — capa 1 por archivo (≈ PostToolUse): Checkstyle del .java editado.
// El feedback va en output.output: el agente lo lee como parte del resultado de su propia edición.
// El formato NO se aplica aquí (removeUnusedImports rompe ediciones en varios pasos, lab S03):
// Spotless corre en session.idle (verify-quality.ts) y en el pre-commit.
import type { Plugin } from "@opencode-ai/plugin"
import path from "node:path"

export const AcdcStyle: Plugin = async ({ $, directory }) => {
  // tool.execute.after no siempre trae los argumentos: la ruta se guarda en before, por callID
  const files = new Map<string, string>()

  return {
    "tool.execute.before": async (input, output) => {
      if (!["edit", "write"].includes(input.tool)) return
      const raw = String(output.args?.filePath ?? "")
      if (raw.endsWith(".java")) files.set(input.callID, path.resolve(directory, raw))
    },

    "tool.execute.after": async (input, output) => {
      const file = files.get(input.callID)
      files.delete(input.callID)                       // sin esto, el Map crece sin límite
      if (!file) return

      // Raíz del árbol del ARCHIVO (worktrees), no el directorio de la sesión
      const top = await $`git -C ${path.dirname(file)} rev-parse --show-toplevel`.quiet().nothrow()
      if (top.exitCode !== 0) return
      const root = path.resolve(top.stdout.toString().trim())
      const rel = path.relative(root, file).replace(/\\/g, "/")
      const m = rel.match(/^src\/(main|test)\/java\/(.+)$/)
      if (!m) return

      // Solo el archivo editado, no todo el proyecto
      const r = await $`bash ./mvnw -q checkstyle:check ${"-Dcheckstyle.includes=" + m[2]}`
        .cwd(root).quiet().nothrow()
      if (r.exitCode === 0) return

      const out = r.stdout.toString() + r.stderr.toString()
      const violations = out.split(/\r?\n/)
        .filter(l => /^\[ERROR\] .*\.java:\[/.test(l))
        .map(l => l.replace(/^\[ERROR\] /, ""))
      output.output += violations.length
        ? `\n\n[AC/DC Checkstyle] ${rel}: corrige cada violación (no añadas supresiones ni toques config/).\n${violations.join("\n")}`
        : `\n\n[AC/DC] No pude ejecutar Checkstyle sobre ${rel} (¿el archivo parsea?). Salida:\n${out.slice(-1500)}`
    },
  }
}
