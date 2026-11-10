#!/usr/bin/env bash
# .claude/hooks/protect-security.sh — PreToolUse, capa 2 (lab S13). Falla CERRADO.
#  1. Supresiones nuevas: nosemgrep / gitleaks:allow no se escriben por ninguna vía.
#  2. Secretos locales: .env y .env.* (salvo .env.example) no se leen ni se listan.
#  3. Hooks de git: commit/push con --no-verify, LEFTHOOK=0 o core.hooksPath quedan bloqueados.
command -v jq >/dev/null || { echo "AC/DC: falta jq; no puedo verificar la capa 2." >&2; exit 2; }

INPUT=$(cat)
TOOL=$(jq -r '.tool_name // empty' <<<"$INPUT")

SUPPRESSION='nosemgrep|gitleaks:allow'

block() { echo "AC/DC: $1" >&2; exit 2; }

suppression_msg='las supresiones (nosemgrep, gitleaks:allow) solo las añade una persona en el PR, con motivo. Si crees que el hallazgo es un falso positivo, explícalo para la descripción del PR y detente; mientras tanto corrige el código.'
env_msg='.env contiene secretos locales y está fuera del alcance del agente. Usa variables de entorno ya configuradas o pide a la persona el nombre de la variable; no leas, copies ni muestres su valor.'

is_env_path() {
  local b; b=$(basename "${1//\\//}")
  [[ "$b" =~ ^\.env(\..+)?$ && "$b" != ".env.example" ]]
}

case "$TOOL" in
  Edit|MultiEdit|Write|NotebookEdit)
    F=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' <<<"$INPUT")
    NEW=$(jq -r '[.tool_input.new_string, .tool_input.content, .tool_input.new_source, (.tool_input.edits // [] | .[].new_string)] | map(select(. != null)) | join("\n")' <<<"$INPUT")
    # La documentación puede nombrar las supresiones; el código y la configuración, no.
    if [[ "$F" != *.md ]] && grep -qiE "$SUPPRESSION" <<<"$NEW"; then block "$suppression_msg"; fi
    is_env_path "$F" && block "$env_msg"
    ;;
  Read)
    is_env_path "$(jq -r '.tool_input.file_path // empty' <<<"$INPUT")" && block "$env_msg"
    ;;
  Grep|Glob)
    TARGETS=$(jq -r '[.tool_input.path, .tool_input.glob, .tool_input.pattern] | map(select(. != null)) | join(" ")' <<<"$INPUT")
    for t in $TARGETS; do is_env_path "$t" && block "$env_msg"; done
    # Grep sin ruta concreta recorre la raíz: también encontraría .env
    if [[ "$TOOL" == "Grep" ]] && jq -e '.tool_input.path == null and .tool_input.glob == null' >/dev/null <<<"$INPUT" \
       && [[ -f "${CLAUDE_PROJECT_DIR:-.}/.env" ]]; then
      block "Grep sin ruta ni glob también lee .env. Acota la búsqueda (path: src, glob: *.java)."
    fi
    ;;
  Bash|PowerShell)
    CMD=$(jq -r '.tool_input.command // empty' <<<"$INPUT")
    grep -qiE "$SUPPRESSION" <<<"$CMD" && block "$suppression_msg"
    # Cada palabra del comando que sea un fichero .env (con o sin ruta) bloquea.
    while IFS= read -r word; do
      [[ -n "$word" ]] && is_env_path "$word" && block "$env_msg"
    done < <(tr -s ' \t"'"'"'=;|&<>(),' '\n' <<<"$CMD")
    # Volcados del entorno completo pueden incluir lo cargado desde .env
    grep -qE '(^|[;&|[:space:]])(printenv|env)([[:space:]]*($|[;&|]))|Get-ChildItem[[:space:]]+env:|\bdotenv\b' <<<"$CMD" \
      && block "no vuelques el entorno completo: puede contener secretos. Consulta solo la variable concreta que necesites y sin mostrar su valor."
    if grep -qE '(^|[^[:alnum:]-])--no-verify([^[:alnum:]-]|$)|LEFTHOOK(_EXCLUDE)?=|core\.hooksPath|HUSKY=0' <<<"$CMD" \
       || grep -qE 'git([[:space:]]+-c[[:space:]]+[^[:space:]]+)*[[:space:]]+commit([[:space:]]+[^[:space:]]+)*[[:space:]]+-[A-Za-z]*n[A-Za-z]*([[:space:]]|$)' <<<"$CMD"; then
      block "los hooks de git (pre-commit con Gitleaks, formato y estilo) no se saltan: nada de --no-verify, -n, LEFTHOOK=0 ni core.hooksPath. Si el pre-commit falla, corrige la causa; si crees que el hook se equivoca, explícalo a la persona y detente."
    fi
    ;;
esac
exit 0
