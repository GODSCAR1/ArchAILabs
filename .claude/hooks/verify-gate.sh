#!/usr/bin/env bash
# .claude/hooks/verify-gate.sh
set -uo pipefail
INPUT=$(cat)
SESSION=$(jq -r '.session_id' <<<"$INPUT")
STATE="${TMPDIR:-/tmp}/acdc-$SESSION"          # contador de intentos POR SESIÓN
# Raíz del árbol en el que trabaja el agente (cwd del JSON); $CLAUDE_PROJECT_DIR falla en worktrees
CWD=$(jq -r '.cwd // empty' <<<"$INPUT")
cd "$(git -C "${CWD:-$CLAUDE_PROJECT_DIR}" rev-parse --show-toplevel 2>/dev/null || echo "$CLAUDE_PROJECT_DIR")"

[[ -z $(git status --porcelain -- src openspec) ]] && exit 0   # atajo: sin cambios en src/ → nada

# Formato determinista sobre el estado COMPLETO del turno (no tras cada edición: lab S03)
./mvnw -q spotless:apply >/dev/null 2>&1 || true   # corrige, no juzga: el veredicto es spotless:check de Verify

if REPORT=$(./verify-quality.sh --fast 2>&1); then
  rm -f "$STATE"; exit 0
fi

N=$(( $(cat "$STATE" 2>/dev/null || echo 0) + 1 ))
if (( N > 3 )); then
  rm -f "$STATE"
  echo "AC/DC: 3 intentos sin pasar Verify. Escalar a humano." >&2
  exit 1        # NO bloquea: Claude termina y el aviso llega al usuario
fi
echo "$N" > "$STATE"
jq -n --arg r "$(tail -n 60 <<<"$REPORT")" --arg n "$N" \
  '{decision:"block",
    reason:("AC/DC Verify falló (intento "+$n+"/3). Corrige la causa; no desactives reglas ni bajes umbrales.\n"+$r)}'