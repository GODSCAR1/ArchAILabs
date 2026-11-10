#!/usr/bin/env bash
# .claude/hooks/security-scan.sh — capa 2 por archivo (lab S13).
#   Claude Code: PostToolUse (Edit|MultiEdit|Write), JSON por stdin.
#   OpenCode:    acdc-security.ts lo invoca como `security-scan.sh <ruta>`.
# Semgrep SOLO con semgrep-rules/ (sin registro: objetivo p95 < 2 s) y Gitleaks sobre el archivo, en paralelo.
# Semgrep usa el CLI nativo (--experimental, osemgrep): mismo motor semgrep-core sin arrancar Python,
# ~2,4 s → décimas en Windows. La auditoría (verify --full, CI) sigue con el CLI estable.
# Los hallazgos vuelven al agente (exit 2). Falla CERRADO: sin herramientas, bloquea.
set -uo pipefail
START=$(date +%s%3N)

to_unix() { local p="$1"; command -v cygpath >/dev/null && p=$(cygpath -u "$p"); echo "${p//\\//}"; }

if [[ $# -ge 1 ]]; then
  F="$1"
else
  command -v jq >/dev/null || { echo "AC/DC: falta jq; no puedo ejecutar la capa 2." >&2; exit 2; }
  F=$(jq -r '.tool_input.file_path // empty') || { echo "AC/DC: entrada del hook no válida (capa 2)." >&2; exit 2; }
fi
[[ -z "$F" ]] && exit 0
F=$(to_unix "$F")
[[ -f "$F" ]] || exit 0          # borrado o renombrado: no hay contenido que analizar

# Raíz del árbol del ARCHIVO (worktrees), igual que verify-file.sh
ROOT=$(git -C "$(dirname "$F")" rev-parse --show-toplevel 2>/dev/null) || exit 0
ROOT=$(to_unix "$ROOT")
REL=${F#"$ROOT"/}
cd "$ROOT" || exit 0

# Los fixtures de las reglas son vulnerables a propósito (y zona protegida): no se analizan aquí.
case "$REL" in semgrep-rules/*|target/*|graphify-out/*) exit 0 ;; esac

for tool in semgrep gitleaks; do
  command -v "$tool" >/dev/null || { echo "AC/DC: falta $tool en el PATH; la capa 2 no puede verificar $REL. Avisa a la persona." >&2; exit 2; }
done

# Ajustes de Semgrep propios de esta ejecución, en ruta nativa (Semgrep es Python de Windows en Git Bash).
sg_settings() { local f; f="$(mktemp -d)/settings.yml"; command -v cygpath >/dev/null && f=$(cygpath -w "$f"); echo "$f"; }

REPORT=""

# --- Secretos (en paralelo con Semgrep): cualquier archivo editado ------------------------------
GL_JSON=$(mktemp)
gitleaks dir "$REL" --config .gitleaks.toml --redact --no-banner --log-level error \
  --report-format json --report-path "$GL_JSON" >/dev/null 2>&1 &
GL_PID=$!

# --- SAST: solo reglas del proyecto, solo código Java -------------------------------------------
if [[ "$REL" == *.java ]]; then
  # Ajustes propios por ejecución: ~/.semgrep/settings.yml compartido da PermissionError en Windows
  # cuando dos Semgrep corren a la vez (lab S13, paso 5).
  SG_OUT=$(SEMGREP_SETTINGS_FILE="$(sg_settings)" SEMGREP_ENABLE_VERSION_CHECK=0 \
    semgrep scan --experimental --config semgrep-rules/ --metrics=off --disable-version-check --quiet \
      --no-git-ignore --json "$REL" 2>/dev/null)
  SG_RC=$?
  if [[ $SG_RC -gt 1 ]] || ! jq -e '.results' >/dev/null 2>&1 <<<"$SG_OUT"; then
    REPORT+="AC/DC: Semgrep no pudo analizar $REL (código $SG_RC). ¿El archivo parsea?"$'\n'
  else
    FINDINGS=$(jq -r '.results[] | "  \(.path):\(.start.line)  [\(.check_id | split(".") | last)] \(.extra.message | gsub("\\s+"; " "))"' <<<"$SG_OUT")
    [[ -n "$FINDINGS" ]] && REPORT+="AC/DC Semgrep (capa 2) en $REL:"$'\n'"$FINDINGS"$'\n'
  fi
fi

wait "$GL_PID"
GL_RC=$?
if [[ $GL_RC -eq 1 ]]; then
  LEAKS=$(jq -r '.[] | "  \(.File):\(.StartLine)  [\(.RuleID)] \(.Description)"' "$GL_JSON" 2>/dev/null)
  REPORT+="AC/DC Gitleaks (capa 2) en $REL: secreto con formato real (valor redactado):"$'\n'"$LEAKS"$'\n'
elif [[ $GL_RC -ne 0 ]]; then
  REPORT+="AC/DC: Gitleaks no pudo analizar $REL (código $GL_RC)."$'\n'
fi
rm -f "$GL_JSON"

# Tiempo del hook para el p95 del paso 8 (target/ no se versiona)
mkdir -p target/acdc && echo "$(( $(date +%s%3N) - START )) $REL" >> target/acdc/security-hook-ms.log

[[ -z "$REPORT" ]] && exit 0
{ echo "$REPORT"
  echo "Corrige la causa en el código (parámetros enlazados o lista blanca, SecureRandom, confinamiento de rutas,"
  echo "cliente desde la cuenta, secretos fuera del repo por variable de entorno). NO añadas nosemgrep ni"
  echo "gitleaks:allow ni toques semgrep-rules/, .semgrepignore o .gitleaks.toml: si crees que es un falso"
  echo "positivo, explícalo a la persona para el PR y detente."; } >&2
exit 2
