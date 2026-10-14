@AGENTS.md

## Específico de Claude Code
- Los hooks de `.claude/settings.json` forman el inner loop AC/DC:
    - SessionStart: inyecta rama, umbrales vigentes y hallazgos abiertos.
    - PreToolUse: bloquea la edición de la zona protegida (`config/`, `.claude/`, `.opencode/`, `.github/`, `pom.xml`, `.env*`).
    - PostToolUse: aplica Spotless al `.java` editado.
    - Stop: ejecuta `./verify-quality.sh --fast`; si falla, no puedes terminar y recibes el informe.
- Si el hook Stop te bloquea, corrige la causa raíz; no intentes terminar sin resolverlo.
- Límite: 3 intentos por sesión. Si se superan, detente y resume para la persona qué falla y qué intentaste.
- Si un hook bloquea una edición en la zona protegida, no busques otra vía (Bash, renombrar archivos): pide permiso.