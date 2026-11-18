// .opencode/lib/acdc.ts — utilidades comunes de los plugins AC/DC para la API V2 de OpenCode (2.x).
// Lab S16: OpenCode 2.x dejó de cargar los plugins V1 ("Plugin must export a default definition with
// an id and an effect or setup function") y el loop de OpenCode quedó apagado sin aviso. Esta API se
// comprobó con una sonda en OpenCode 2.3.0 (docs/lab-s16.md, paso 9):
//  - export default { id, setup(ctx) }: no hace falta importar el SDK.
//  - ctx.tool.hook("execute.before" | "execute.after", e): e.tool, e.input, e.id, e.sessionID;
//    en after, e.status ("completed" | "error") y e.result.content (lista de {type, text}).
//  - Herramientas: "shell" (input.command), "read" (input.path), "patch" (input.patchText).
//  - ctx.event.subscribe(): eventos con e.type y e.data (session.created, session.execution.succeeded).
//  - ctx.session.hook("context", e): e.system (lista de {type, text}) antes de cada petición al modelo.
//  - No hay shell de Bun ni forma de escribir mensajes en la sesión (ctx.session.prompt no existe).
import { spawn, spawnSync } from "node:child_process"

export type RunResult = { code: number; stdout: string; stderr: string }
type RunOptions = { cwd: string; env?: NodeJS.ProcessEnv; input?: string }

// Ejecuta un proceso sin bloquear el bucle de eventos (Verify tarda minutos).
export function run(command: string, args: string[], options: RunOptions): Promise<RunResult> {
  return new Promise(resolve => {
    const child = spawn(command, args, { cwd: options.cwd, env: options.env ?? process.env, windowsHide: true })
    let stdout = ""
    let stderr = ""
    child.stdout?.on("data", chunk => (stdout += chunk))
    child.stderr?.on("data", chunk => (stderr += chunk))
    child.on("error", err => resolve({ code: 127, stdout, stderr: stderr + String(err) }))
    child.on("close", code => resolve({ code: code ?? 1, stdout, stderr }))
    child.stdin?.end(options.input ?? "")
  })
}

// Versión síncrona para lo que debe estar listo antes de responder (contexto de sesión).
export function runSync(command: string, args: string[], options: RunOptions): RunResult {
  const r = spawnSync(command, args, {
    cwd: options.cwd, env: options.env ?? process.env, input: options.input ?? "", encoding: "utf8", windowsHide: true,
  })
  return { code: r.status ?? 1, stdout: r.stdout ?? "", stderr: (r.stderr ?? "") + (r.error ? String(r.error) : "") }
}

export function isShell(tool: string): boolean {
  return tool === "shell" || tool === "bash"
}

// Rutas que toca una herramienta de edición: edit/write (path) o patch/apply_patch (patchText con
// "*** Add|Update|Delete File: <ruta>" y "*** Move to: <ruta>").
export function editedPaths(tool: string, input: any): string[] {
  if (tool === "edit" || tool === "write") {
    const file = input?.path ?? input?.filePath ?? input?.file_path
    return file ? [String(file)] : []
  }
  if (tool === "patch" || tool === "apply_patch") {
    const text = String(input?.patchText ?? input?.patch ?? "")
    return [...text.matchAll(/^\*\*\* (?:(?:Add|Update|Delete) File|Move to): (.+?)\s*$/gm)].map(m => m[1])
  }
  return []
}

// El agente lee el feedback como parte del resultado de su propia herramienta.
export function appendToResult(event: any, text: string): void {
  if (!event?.result) return
  if (Array.isArray(event.result.content)) event.result.content.push({ type: "text", text })
  else event.result.content = [{ type: "text", text }]
}

// Suscripción a eventos; cada manejador corre aparte para que uno lento (Verify) no frene a los demás.
export function subscribe(ctx: any, onEvent: (event: any) => unknown): () => void {
  const controller = new AbortController()
  void (async () => {
    try {
      for await (const event of ctx.event.subscribe({ signal: controller.signal })) {
        void Promise.resolve()
          .then(() => onEvent(event))
          .catch(err => console.error("AC/DC: manejador de eventos", err))
      }
    } catch (err) {
      if (!controller.signal.aborted) console.error("AC/DC: suscripción a eventos", err)
    }
  })()
  return () => controller.abort()
}

// Canario del harness (lab S16): cada plugin marca un latido en el hook "context" de cada sesión;
// verify-quality bloquea las herramientas que no son de lectura si en esa sesión falta el latido de
// alguno. Un registro "al cargar" no sirve: OpenCode recarga plugins dentro del mismo proceso y
// globalThis conserva a los que ya no están (el primer intento dio "faltan=[]" con un plugin quitado).
export const EXPECTED_PLUGINS = ["acdc.security", "acdc.specs", "acdc.style", "acdc.verify-quality", "graphify"]

export function markAlive(id: string, sessionID: string): void {
  const g = globalThis as any
  g.__acdcAlive ??= new Map<string, Set<string>>()
  if (!g.__acdcAlive.has(sessionID)) g.__acdcAlive.set(sessionID, new Set<string>())
  g.__acdcAlive.get(sessionID).add(id)
}

export async function heartbeat(ctx: any, id: string): Promise<void> {
  await ctx.session.hook("context", (event: any) => markAlive(id, event.sessionID))
}

export function alivePlugins(sessionID: string): string[] {
  return [...((globalThis as any).__acdcAlive?.get(sessionID) ?? [])].sort()
}

export function missingPlugins(sessionID: string): string[] {
  const alive = new Set(alivePlugins(sessionID))
  return EXPECTED_PLUGINS.filter(id => !alive.has(id))
}
