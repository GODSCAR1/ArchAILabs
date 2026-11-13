#!/usr/bin/env bash
# .claude/hooks/verify-file.sh — PostToolUse (Edit|MultiEdit|Write) y plugin de OpenCode (ruta como $1)
# Capa 1 por archivo; lo que falla vuelve al agente (stderr + exit 2):
#   1. Checkstyle SOLO del .java editado.
#   2. compile + SpotBugs/FindSecBugs de la clase editada (src/main). Por la compilación llegan
#      también los errores de javac, Error Prone y NullAway.
#   3. PMD (con caché), mostrando solo la clase editada.
# El formato NO se aplica aquí: removeUnusedImports sobre un estado intermedio borra imports que el
# agente aún no ha usado (lab S03, prueba 3b). Spotless se aplica al final del turno (verify-gate.sh)
# y en el pre-commit. El ruleset no tiene reglas de formato, así que el veredicto no depende de él.
if [[ -n "${1:-}" ]]; then
  F=$1                                   # OpenCode: el plugin pasa la ruta como argumento
else
  command -v jq >/dev/null || { echo "AC/DC: falta jq; no puedo verificar el archivo." >&2; exit 2; }
  F=$(jq -r '.tool_input.file_path // empty') || { echo "AC/DC: entrada del hook no válida; no puedo verificar el archivo." >&2; exit 2; }
fi
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
CLASS=${INC%.java}; CLASS=${CLASS//\//.}      # nombre completo de la clase

cd "$ROOT" || exit 0

# 1. Estilo: solo este archivo
if ! OUT=$(./mvnw -q checkstyle:check -Dcheckstyle.includes="$INC" 2>&1); then
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
fi

# 2. Bugs: compila y analiza solo la clase editada (el plugin no analiza los tests)
if [[ "$REL" == src/main/java/* ]]; then
  if ! OUT=$(./mvnw -q compile spotbugs:check -Dspotbugs.onlyAnalyze="$CLASS" 2>&1); then
    V=$(grep -E '^\[ERROR\] ((High|Medium|Low):|.*\.java:\[)' <<<"$OUT" | sed -E 's/^\[ERROR\] //' | sort -u | head -15)
    if [[ -n "$V" ]]; then
      { echo "AC/DC compilación/SpotBugs en $REL: corrige el código. No lo suprimas (@SuppressWarnings, @SuppressFBWarnings) ni toques config/ o pom.xml."
        echo "$V"; } >&2
    else
      { echo "AC/DC: no pude compilar o analizar $REL con SpotBugs. Salida:"
        tail -n 20 <<<"$OUT"; } >&2
    fi
    exit 2
  fi
fi

# 3. Malas prácticas: PMD sin -q (las violaciones salen como [WARNING]); se decide por la salida
V=$(./mvnw -B pmd:check 2>&1 | grep -F "PMD Failure: $CLASS:" | sed -E 's/^\[[A-Z]+\] //' | sort -u | head -15)
if [[ -n "$V" ]]; then
  { echo "AC/DC PMD en $REL: corrige cada mala práctica (no uses NOPMD ni @SuppressWarnings, no toques config/)."
    echo "$V"; } >&2
  exit 2
fi
exit 0
