import type { Plugin } from "@opencode-ai/plugin"

const MAX_ATTEMPTS = 3
// Zona protegida: misma lista que protect-verify.sh de Claude Code
const PROTECTED = /(^|\/)(config|\.claude|\.opencode|\.github)\/|(^|\/)pom\.xml$|(^|\/)\.env/

export const VerifyQuality: Plugin = async ({ client, $, directory }) => {
    const attempts = new Map<string, number>()
    const running = new Set<string>()

    return {
        // ── Guía dura: bloquea editar la configuración de Verify ──────────────
        "tool.execute.before": async (input, output) => {
            if (!["edit", "write"].includes(input.tool)) return
            const file = String(output.args?.filePath ?? "").replace(/\\/g, "/") // rutas Windows → /
            if (PROTECTED.test(file)) {
                throw new Error(`AC/DC: ${file} está protegido. Pide permiso a un humano.`)
            }
        },

        // ── Verify + Solve al terminar la sesión (≈ Stop de Claude Code) ─────
        event: async ({ event }) => {
            if (event.type !== "session.idle") return
            const id = event.properties.sessionID
            if (running.has(id)) return               // evita dos verificaciones a la vez
            running.add(id)
            try {
                // Atajo: sin cambios en src/ no hay nada que verificar
                const changes = await $`git status --porcelain -- src`.cwd(directory).quiet().nothrow()
                if (changes.stdout.toString().trim() === "") return

                const r = await $`bash ./verify-quality.sh --fast`.cwd(directory).quiet().nothrow()
                if (r.exitCode === 0) { attempts.delete(id); return }

                const n = (attempts.get(id) ?? 0) + 1
                attempts.set(id, n)
                if (n > MAX_ATTEMPTS + 1) return       // ya se escaló: no insistir más

                const report = (r.stdout.toString() + r.stderr.toString()).slice(-3000)
                const text = n > MAX_ATTEMPTS
                    ? `[AC/DC] ${MAX_ATTEMPTS} intentos sin pasar Verify. DETENTE: no sigas modificando código. ` +
                    `Resume para la persona qué falla y qué intentaste.\n${report}`
                    : `[AC/DC Verify ${n}/${MAX_ATTEMPTS}] Falló:\n${report}\n` +
                    `Corrige la causa. No desactives reglas, no bajes umbrales, no uses @Disabled ni borres tests.`

                await client.session.prompt({
                    path: { id },
                    body: { parts: [{ type: "text", text }] },
                })
            } finally {
                running.delete(id)
            }
        },
    }
}