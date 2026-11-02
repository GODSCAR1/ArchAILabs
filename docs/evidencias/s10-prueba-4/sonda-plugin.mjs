// Sonda del plugin de OpenCode (lab S10, prueba 4). Uso, desde la raíz del repo:
//   cp .opencode/plugins/verify-quality.ts /tmp/vq.mts && cp docs/evidencias/s10-prueba-4/sonda-plugin.mjs /tmp/ \
//   && node --experimental-strip-types /tmp/sonda-plugin.mjs "$PWD"
import { VerifyQuality } from "./vq.mts";
const dir = process.argv[2];
const hooks = await VerifyQuality({ client: {}, $: null, directory: dir });
const V = "src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/out/mongo/CatalogSchemaValidator.java";
const cases = [
  ["edit validador", { tool: "edit" }, { args: { filePath: dir + "/" + V } }],
  ["write validador", { tool: "write" }, { args: { filePath: V } }],
  ["bash sed -i validador", { tool: "bash" }, { args: { command: "sed -i '1i // x' " + V } }],
  ["bash cat validador (lectura)", { tool: "bash" }, { args: { command: "cat " + V } }],
  ["edit CatalogIndexInitializer", { tool: "edit" }, { args: { filePath: V.replace("CatalogSchemaValidator", "CatalogIndexInitializer") } }],
];
for (const [name, input, output] of cases) {
  try { await hooks["tool.execute.before"](input, output); console.log("PERMITIDO | " + name); }
  catch (e) { console.log("BLOQUEADO | " + name + " -> " + e.message); }
}
