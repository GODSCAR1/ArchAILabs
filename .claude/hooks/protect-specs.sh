#!/usr/bin/env bash
# .claude/hooks/protect-specs.sh — PreToolUse: specs aprobadas y archivo de changes protegidos;
# archivar lo hace una persona. Falla cerrado.
command -v jq >/dev/null || { echo "AC/DC: falta jq; no puedo verificar las specs protegidas." >&2; exit 2; }
INPUT=$(cat)
TOOL=$(jq -r '.tool_name' <<<"$INPUT")

if [[ "$TOOL" == "Bash" || "$TOOL" == "PowerShell" ]]; then
  CMD=$(jq -r '.tool_input.command // empty' <<<"$INPUT")
  if grep -qiE '(^|[;&|[:space:]])openspec[[:space:]]+archive' <<<"$CMD"; then
    echo "AC/DC: archivar un change lo hace una persona tras revisar el PR. Termina las tareas y avisa." >&2
    exit 2
  fi
  if grep -qE 'openspec[\\/](specs|changes[\\/]archive)([\\/]|[[:space:]]|$)' <<<"$CMD" \
     && grep -qiE '(>|sed -i|rm |mv |cp |tee |set-content|add-content|out-file|copy-item|move-item|remove-item|new-item)' <<<"$CMD"; then
    echo "AC/DC: openspec/specs y el archivo de changes están protegidos." >&2
    exit 2
  fi
  exit 0
fi

F=$(jq -r '.tool_input.file_path // empty' <<<"$INPUT")
[[ -n "$F" ]] || exit 0
command -v cygpath >/dev/null && F=$(cygpath -u "$F")
F="${F//\\//}"
case "$F" in
  */openspec/specs/*|*/openspec/changes/archive/*|openspec/specs/*|openspec/changes/archive/*)
    echo "AC/DC: $F es una spec aprobada. No la edites: crea o actualiza un change en openspec/changes/<nombre>/ con un delta (ADDED/MODIFIED/REMOVED)." >&2
    exit 2 ;;
esac
exit 0
