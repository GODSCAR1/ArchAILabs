#!/usr/bin/env bash
# .claude/hooks/guide-context.sh
command -v jq >/dev/null || { echo "AC/DC: falta jq" >&2; exit 0; }
cd "${CLAUDE_PROJECT_DIR:-$PWD}"
BRANCH=$(git branch --show-current)
FINDINGS=$(head -n 30 .acdc/open-findings.md 2>/dev/null)
PIT=$(tr -d "\r" < pom.xml 2>/dev/null | sed -n "s:.*<pit.mutationThreshold>\([0-9]*\)</pit.mutationThreshold>.*:\1:p" | head -1)
# Lab S14: en la prueba 4 el agente solo veía el umbral de PIT (aún sin plugin) y confundió los dos.
BRANCH_MIN=$(tr -d '\r' < pom.xml 2>/dev/null | sed -n 's:.*<acdc.coverage.branch.minimum>\(.*\)</acdc.coverage.branch.minimum>.*:\1:p' | head -1)

# Lab S16: sin Docker, los *IT (Testcontainers) no pueden arrancar. Avisarlo al empezar evita que el agente
# "arregle" los tests sustituyendo contenedores por mocks o H2.
DOCKER_WARN=""
if ! timeout 10 docker info >/dev/null 2>&1; then
  DOCKER_WARN="
AVISO DE ENTORNO: Docker no responde. Los *IT (Testcontainers) fallarán por entorno, no por código: no sustituyas contenedores por mocks, H2 ni @MockitoBean, no desactives tests; avisa a la persona para que arranque Docker."
fi

# Lab S16 (canario): AGENTS.md pide al agente comprobar esta marca. Si no le llega, el harness no se cargó.
CTX="AC/DC harness activo (guía de sesión cargada por el harness).
Rama: $BRANCH
Umbral PIT de pricing (execution pit-pricing, STRONGER; verify-quality.sh capa 3): ${PIT:-sin definir}% — no se baja; supervivientes en .acdc/pit-survivors.md
Umbral de cobertura de RAMAS (JaCoCo, por paquete de identity, verify-quality.sh): ${BRANCH_MIN:-sin definir} — no se baja
Hallazgos abiertos (no introduzcas más de estos tipos):
${FINDINGS:-ninguno}${DOCKER_WARN}"

jq -n --arg c "$CTX" '{hookSpecificOutput:{hookEventName:"SessionStart", additionalContext:$c}}'