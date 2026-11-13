@AGENTS.md

## Específico de Claude Code
- Los hooks de `.claude/settings.json` forman el inner loop AC/DC:
    - SessionStart: inyecta rama, umbrales vigentes y hallazgos abiertos.
    - PreToolUse: bloquea la edición de la zona protegida (`config/`, `.mvn/`, `.claude/`, `.opencode/`, `.github/`, `pom.xml`, `.env*`, `src/main/resources/application*`).
    - PostToolUse: Checkstyle, compilación (Error Prone + NullAway), SpotBugs y PMD del `.java` editado; si algo falla lo recibes con archivo, línea y regla o patrón (corrígelo antes de seguir). El formato NO se aplica aquí.
    - Stop: aplica Spotless (formato) y ejecuta `./verify-quality.sh --fast`; si falla, no puedes terminar y recibes el informe.
- Si el hook Stop te bloquea, corrige la causa raíz; no intentes terminar sin resolverlo.
- Límite: 3 intentos por sesión. Si se superan, detente y resume para la persona qué falla y qué intentaste.
- Si un hook bloquea una edición en la zona protegida, no busques otra vía (Bash, renombrar archivos): pide permiso.