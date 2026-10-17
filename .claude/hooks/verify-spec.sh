#!/usr/bin/env bash
# .claude/hooks/verify-spec.sh — PostToolUse (Edit|MultiEdit|Write): valida el change editado.
# Raíz derivada de la ruta del archivo (worktrees), nunca de $CLAUDE_PROJECT_DIR. Falla cerrado.
set -uo pipefail
command -v jq >/dev/null || { echo "AC/DC: falta jq; no puedo validar el change." >&2; exit 2; }
INPUT=$(cat)
F=$(jq -r '.tool_input.file_path // empty' <<<"$INPUT")
[[ -n "$F" ]] || exit 0
command -v cygpath >/dev/null && F=$(cygpath -u "$F")
F="${F//\\//}"
[[ "$F" == */openspec/changes/* && "$F" != */openspec/changes/archive/* ]] || exit 0
command -v openspec >/dev/null || { echo "AC/DC: falta openspec en el PATH de bash; no puedo validar." >&2; exit 2; }
CHANGE=$(sed -E 's#.*/openspec/changes/([^/]+)/.*#\1#' <<<"$F")
ROOT=${F%%/openspec/changes/*}
cd "$ROOT" || exit 2
OUT=$(OPENSPEC_TELEMETRY=0 openspec validate "$CHANGE" --strict --no-interactive 2>&1)
if [[ $? -ne 0 ]]; then
  {
    echo "OpenSpec: el change '$CHANGE' no es válido. Corrige estos problemas:"
    grep -E '\[(ERROR|WARNING|INFO)\]' <<<"$OUT" | head -10
  } >&2
  exit 2
fi
exit 0
