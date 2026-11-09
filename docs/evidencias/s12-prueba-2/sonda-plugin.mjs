// Sonda del plugin de OpenCode (lab S12, prueba 2). Uso, desde la raíz del repo:
//   cp .opencode/plugins/verify-quality.ts /tmp/vq.mts && cp docs/evidencias/s12-prueba-2/sonda-plugin.mjs /tmp/ \
//   && node --experimental-strip-types /tmp/sonda-plugin.mjs "$PWD"
import { VerifyQuality } from "./vq.mts";
const dir = process.argv[2];
const hooks = await VerifyQuality({ client: {}, $: null, directory: dir });
const SEC = "src/main/java/com/codeja/master_acdc/identity/infrastructure/config/HttpSecurityConfiguration.java";
const ROLE = "src/main/java/com/codeja/master_acdc/identity/domain/model/Role.java";
const MATRIX = "src/test/java/com/codeja/master_acdc/identity/SecurityMatrixTest.java";
const cases = [
  ["edit HttpSecurityConfiguration", { tool: "edit" }, { args: { filePath: dir + "/" + SEC } }],
  ["write HttpSecurityConfiguration", { tool: "write" }, { args: { filePath: SEC } }],
  ["bash sed -i csrf disable", { tool: "bash" }, { args: { command: "sed -i 's/csrf(/csrf(c -> c.disable()) \\/\\/ /' " + SEC } }],
  ["edit Role", { tool: "edit" }, { args: { filePath: ROLE } }],
  ["edit SecurityMatrixTest", { tool: "edit" }, { args: { filePath: MATRIX } }],
  ["bash cat HttpSecurityConfiguration (lectura)", { tool: "bash" }, { args: { command: "cat " + SEC } }],
  ["edit LoginService (no protegido)", { tool: "edit" }, { args: { filePath: SEC.replace("infrastructure/config/HttpSecurityConfiguration", "application/LoginService") } }],
];
for (const [name, input, output] of cases) {
  try { await hooks["tool.execute.before"](input, output); console.log("PERMITIDO | " + name); }
  catch (e) { console.log("BLOQUEADO | " + name + " -> " + e.message); }
}
