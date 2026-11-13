#!/usr/bin/env bash
# .claude/hooks/verify-file.sh — PostToolUse (Edit|MultiEdit|Write) y plugin de OpenCode
# Entrada: JSON del hook por stdin (Claude Code) o rutas como argumentos (OpenCode: un parche
# puede tocar varios .java y se verifican todos con una sola pasada de Maven por paso).
# Capa 1 por archivo; lo que falla vuelve al agente (stderr + exit 2):
#   1. Checkstyle SOLO de los .java editados.
#   2. compile + SpotBugs/FindSecBugs de las clases editadas (src/main). Por la compilación llegan
#      también los errores de javac, Error Prone y NullAway.
#   3. PMD (con caché), mostrando solo las clases editadas.
# El formato NO se aplica ni se comprueba aquí: removeUnusedImports sobre un estado intermedio borra
# imports que el agente aún no ha usado (lab S03, prueba 3b). Spotless se aplica al final del turno
# (verify-gate.sh) y en el pre-commit. Por eso el paso 2 salta spotless:check y checkstyle:check,
# que están ligados a la fase validate y romperían la compilación de un estado intermedio.
if (( $# > 0 )); then
  FILES=("$@")
else
  command -v jq >/dev/null || { echo "AC/DC: falta jq; no puedo verificar el archivo." >&2; exit 2; }
  F=$(jq -r '.tool_input.file_path // empty') || { echo "AC/DC: entrada del hook no válida; no puedo verificar el archivo." >&2; exit 2; }
  FILES=("$F")
fi

# Normalizar rutas (Windows: C:\... y C:/... → /c/...)
to_unix() { local p="$1"; command -v cygpath >/dev/null && p=$(cygpath -u "$p"); echo "${p//\\//}"; }

ROOT=""; RELS=(); INCS=(); MAIN=(); PATS=()
for F in "${FILES[@]}"; do
  [[ "$F" == *.java ]] || continue
  F=$(to_unix "$F")
  if [[ -z "$ROOT" ]]; then
    # Raíz del árbol del ARCHIVO, no $CLAUDE_PROJECT_DIR (en un worktree apunta al checkout principal)
    ROOT=$(git -C "$(dirname "$F")" rev-parse --show-toplevel 2>/dev/null) || exit 0
    ROOT=$(to_unix "$ROOT")
  fi
  REL=${F#"$ROOT"/}
  case "$REL" in
    src/main/java/*) INC=${REL#src/main/java/} ;;
    src/test/java/*) INC=${REL#src/test/java/} ;;
    *) continue ;;
  esac
  CLASS=${INC%.java}; CLASS=${CLASS//\//.}      # nombre completo de la clase
  RELS+=("$REL"); INCS+=("$INC"); PATS+=("PMD Failure: $CLASS:")
  [[ "$REL" == src/main/java/* ]] && MAIN+=("$CLASS")
done
(( ${#RELS[@]} > 0 )) || exit 0
join() { local IFS=,; echo "$*"; }
WHAT=$(join "${RELS[@]}"); WHAT=${WHAT//,/, }

cd "$ROOT" || exit 0

# 1. Estilo: solo los archivos editados
if ! OUT=$(./mvnw -q checkstyle:check -Dcheckstyle.includes="$(join "${INCS[@]}")" 2>&1); then
  V=$(grep -E '^\[ERROR\] .*\.java:\[' <<<"$OUT" | sed -E 's/^\[ERROR\] //')
  if [[ -n "$V" ]]; then
    { echo "AC/DC Checkstyle en $WHAT: corrige cada violación (no añadas supresiones ni toques config/)."
      echo "$V"; } >&2
  else
    # Falla cerrado: si Checkstyle no pudo ejecutarse (archivo que no parsea, Maven roto), el agente se entera
    { echo "AC/DC: no pude ejecutar Checkstyle sobre $WHAT (¿el archivo parsea?). Salida:"
      tail -n 20 <<<"$OUT"; } >&2
  fi
  exit 2
fi

# 2. Bugs: compila y analiza solo las clases editadas (el plugin no analiza los tests)
if (( ${#MAIN[@]} > 0 )); then
  if ! OUT=$(./mvnw -q compile spotbugs:check -Dspotless.check.skip=true -Dcheckstyle.skip=true \
               -Dspotbugs.onlyAnalyze="$(join "${MAIN[@]}")" 2>&1); then
    V=$(grep -E '^\[ERROR\] ((High|Medium|Low):|.*\.java:\[)' <<<"$OUT" | sed -E 's/^\[ERROR\] //' | sort -u | head -20)
    if [[ -n "$V" ]]; then
      { echo "AC/DC compilación/SpotBugs en $WHAT: corrige el código. No lo suprimas (@SuppressWarnings, @SuppressFBWarnings) ni toques config/ o pom.xml."
        echo "$V"; } >&2
    else
      { echo "AC/DC: no pude compilar o analizar $WHAT con SpotBugs. Salida:"
        tail -n 20 <<<"$OUT"; } >&2
    fi
    exit 2
  fi
fi

# 3. Malas prácticas: PMD sin -q (las violaciones salen como [WARNING]); se decide por la salida
V=$(./mvnw -B pmd:check 2>&1 | grep -F -f <(printf '%s\n' "${PATS[@]}") | sed -E 's/^\[[A-Z]+\] //' | sort -u | head -20)
if [[ -n "$V" ]]; then
  { echo "AC/DC PMD en $WHAT: corrige cada mala práctica (no uses NOPMD ni @SuppressWarnings, no toques config/)."
    echo "$V"; } >&2
  exit 2
fi
exit 0
