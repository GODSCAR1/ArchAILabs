// graphify OpenCode plugin
// Injects a knowledge graph reminder before the first shell tool call when the graph exists.
//
// IMPORTANT: keep the reminder string free of backticks and $(...) constructs.
// The hook prepends `echo "<reminder>" ; <cmd>` to the agent's shell command;
// backticks inside the double-quoted echo trigger bash command substitution,
// which both corrupts tool output and silently executes the very graphify
// command we are only suggesting. Plain words render fine in opencode's TUI.
//
// Lab S16: migrated to the OpenCode V2 plugin API (default export { id, setup }; the shell tool is
// now called "shell" and its arguments live in event.input).
import { existsSync } from "fs";
import { join } from "path";

export default {
  id: "graphify",
  async setup(ctx) {
    // Canario AC/DC (lab S16): mismo latido que heartbeat() de .opencode/lib/acdc.ts
    await ctx.session.hook("context", (event) => {
      const alive = (globalThis.__acdcAlive ??= new Map());
      if (!alive.has(event.sessionID)) alive.set(event.sessionID, new Set());
      alive.get(event.sessionID).add("graphify");
    });
    const directory = ctx.location.directory;
    let reminded = false;

    await ctx.tool.hook("execute.before", (event) => {
      if (reminded) return;
      if (event.tool !== "shell" && event.tool !== "bash") return;
      if (!existsSync(join(directory, "graphify-out", "graph.json"))) return;
      // ';' not '&&' — Windows PowerShell 5.1 rejects '&&' as a statement separator (#1646).
      event.input.command =
        'echo "[graphify] knowledge graph at graphify-out/. For focused questions, run graphify query with your question (scoped subgraph, usually much smaller than GRAPH_REPORT.md) instead of grepping raw files. Read GRAPH_REPORT.md only for broad architecture context." ; ' +
        event.input.command;
      reminded = true;
    });
  },
};
