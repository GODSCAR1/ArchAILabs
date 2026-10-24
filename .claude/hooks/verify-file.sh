#!/usr/bin/env bash
# .claude/hooks/verify-file.sh — PostToolUse (Edit|MultiEdit|Write)
# Capa 1 por archivo: Checkstyle SOLO del .java editado; las violaciones vuelven al agente (exit 2).
# El formato NO se aplica aquí: removeUnusedImports sobre un estado intermedio borra imports que el
# agente aún no ha usado (lab S03, prueba 3b). Spotless se aplica al final del turno (verify-gate.sh)
# y en el pre-commit. El ruleset no tiene reglas de formato, así que el veredicto no depende de él.
command -v jq >/dev/null || { echo "AC/DC: falta jq; no puedo verificar el estilo." >&2; exit 2; }

F=$(jq -r '.tool_input.file_path // empty') || { echo "AC/DC: entrada del hook no válida; no puedo verificar el estilo." >&2; exit 2; }
[[ "$F" == *.java ]] || exit 0

# Normalizar rutas (Windows: C:\... y C:/... → /c/...)
to_unix() { local p="$1"; command -v cygpath >/dev/null && p=$(cygpath -u "$p"); echo "${p//\\//}"; }
F=$(to_unix "$F")

# Raíz del árbol del ARCHIVO, no $CLAUDE_PROJECT_DIR (en un worktree apunta al checkout principal)
ROOT=$(git -C "$(dirname "$F")" rev-parse --show-toplevel 2>/dev/null) || exit 0
ROOT=$(to_unix "$ROOT")
REL=${F#"$ROOT"/}
case "$REL" in
  src/main/java/*) INC=${REL#src/main/java/} ;;
  src/test/java/*) INC=${REL#src/test/java/} ;;
  *) exit 0 ;;
esac

cd "$ROOT" || exit 0
if OUT=$(./mvnw -q checkstyle:check -Dcheckstyle.includes="$INC" 2>&1); then
  exit 0
fi

V=$(grep -E '^\[ERROR\] .*\.java:\[' <<<"$OUT" | sed -E 's/^\[ERROR\] //')
if [[ -n "$V" ]]; then
  { echo "AC/DC Checkstyle en $REL: corrige cada violación (no añadas supresiones ni toques config/)."
    echo "$V"; } >&2
else
  # Falla cerrado: si Checkstyle no pudo ejecutarse (archivo que no parsea, Maven roto), el agente se entera
  { echo "AC/DC: no pude ejecutar Checkstyle sobre $REL (¿el archivo parsea?). Salida:"
    tail -n 20 <<<"$OUT"; } >&2
fi
exit 2
