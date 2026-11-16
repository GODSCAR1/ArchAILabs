#!/usr/bin/env bash
# .claude/hooks/guide-context.sh
command -v jq >/dev/null || { echo "AC/DC: falta jq" >&2; exit 0; }
cd "${CLAUDE_PROJECT_DIR:-$PWD}"
BRANCH=$(git branch --show-current)
FINDINGS=$(head -n 30 .acdc/open-findings.md 2>/dev/null)
PIT=$(tr -d "\r" < pom.xml 2>/dev/null | sed -n "s:.*<pit.mutationThreshold>\([0-9]*\)</pit.mutationThreshold>.*:\1:p" | head -1)
# Lab S14: en la prueba 4 el agente solo veía el umbral de PIT (aún sin plugin) y confundió los dos.
BRANCH_MIN=$(tr -d '\r' < pom.xml 2>/dev/null | sed -n 's:.*<acdc.coverage.branch.minimum>\(.*\)</acdc.coverage.branch.minimum>.*:\1:p' | head -1)

CTX="Rama: $BRANCH
Umbral PIT de pricing (execution pit-pricing, STRONGER; verify-quality.sh capa 3): ${PIT:-sin definir}% — no se baja; supervivientes en .acdc/pit-survivors.md
Umbral de cobertura de RAMAS (JaCoCo, por paquete de identity, verify-quality.sh): ${BRANCH_MIN:-sin definir} — no se baja
Hallazgos abiertos (no introduzcas más de estos tipos):
${FINDINGS:-ninguno}"

jq -n --arg c "$CTX" '{hookSpecificOutput:{hookEventName:"SessionStart", additionalContext:$c}}'