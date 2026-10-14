#!/usr/bin/env bash
# .claude/hooks/guide-context.sh
command -v jq >/dev/null || { echo "AC/DC: falta jq" >&2; exit 0; }
cd "$CLAUDE_PROJECT_DIR"
BRANCH=$(git branch --show-current)
FINDINGS=$(head -n 30 .acdc/open-findings.md 2>/dev/null)
PIT=$(grep -o 'mutationThreshold>[0-9]*' pom.xml 2>/dev/null | grep -o '[0-9]*')

CTX="Rama: $BRANCH
Umbral PIT vigente: ${PIT:-sin definir}%
Hallazgos abiertos (no introduzcas más de estos tipos):
${FINDINGS:-ninguno}"

jq -n --arg c "$CTX" '{hookSpecificOutput:{hookEventName:"SessionStart", additionalContext:$c}}'