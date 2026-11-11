#!/usr/bin/env bash
# scripts/open-findings.sh — genera .acdc/open-findings.md a partir de los SARIF de la capa 2 (lab S13).
# Uso: scripts/open-findings.sh <sarif>...   (los inexistentes se ignoran)
# guide-context.sh inyecta este fichero al empezar cada sesión del agente: "no introduzcas más de estos tipos".
set -euo pipefail
command -v jq >/dev/null || { echo "open-findings: falta jq" >&2; exit 2; }
OUT=.acdc/open-findings.md
mkdir -p .acdc

ROWS=""
for sarif in "$@"; do
  [[ -s "$sarif" ]] || continue
  ROWS+=$(jq -r '
    .runs[] as $run
    | ($run.tool.driver.name) as $tool
    | $run.results[]?
    | [ $tool,
        (.ruleId | split(".") | last),
        (.level // "warning"),
        ((.locations[0].physicalLocation.artifactLocation.uri // "?") + ":" + ((.locations[0].physicalLocation.region.startLine // 0) | tostring)),
        (.message.text | gsub("[\\r\\n|]+"; " ") | .[0:160]) ]
    | "| " + join(" | ") + " |"' "$sarif")$'\n'
done
ROWS=$(sed '/^$/d' <<<"$ROWS" | sort -u)

{
  echo "# Hallazgos abiertos de la capa 2"
  echo
  echo "_Generado por \`scripts/open-findings.sh\` a partir del SARIF de Semgrep y Gitleaks. No editar a mano._"
  echo
  if [[ -z "$ROWS" ]]; then
    echo "Ninguno."
  else
    echo "| Herramienta | Regla | Nivel | Ubicación | Mensaje |"
    echo "|---|---|---|---|---|"
    echo "$ROWS"
  fi
} > "$OUT"
echo "open-findings: $(grep -c '^| [A-Za-z]' <<<"$ROWS" || true) hallazgo(s) en $OUT"
