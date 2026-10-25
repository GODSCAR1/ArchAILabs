#!/usr/bin/env bash
# verify-quality.sh  —  uso: verify-quality.sh --fast | --full
set -euo pipefail
case "${1:---fast}" in
  --fast|--full) ;;
  *) echo "uso: verify-quality.sh --fast | --full" >&2; exit 64 ;;
esac
export OPENSPEC_TELEMETRY=0
echo "▶ Specs"
openspec validate --all --strict --no-interactive
openspec validate --archived --no-interactive
bash scripts/spec-trace.sh
echo "▶ Configuración JPA (ADR docs/decisiones/listado-de-pedidos.md)"
# Segunda capa: no depende de que el guard de la zona protegida haya funcionado (lab S08, prueba 4).
JPA_PROPS=src/main/resources/application.properties
tr -d '\r' < "$JPA_PROPS" | grep -qx 'spring.jpa.open-in-view=false' \
  || { echo "AC/DC: spring.jpa.open-in-view debe ser false en $JPA_PROPS. Carga lo necesario en la consulta del caso de uso; no reactives OSIV." >&2; exit 1; }
tr -d '\r' < "$JPA_PROPS" | grep -qx 'spring.jpa.properties.hibernate.default_batch_fetch_size=50' \
  || { echo "AC/DC: default_batch_fetch_size=50 es la red de seguridad del proyecto ($JPA_PROPS); no lo cambies." >&2; exit 1; }
if grep -rqs 'enable_lazy_load_no_trans' src/main/resources; then
  echo "AC/DC: hibernate.enable_lazy_load_no_trans está prohibido (abre una sesión por acceso: N+1 garantizado)." >&2; exit 1
fi
echo "▶ Capa 1: formato y estilo"
./mvnw -q spotless:check
./mvnw -q checkstyle:check
echo "▶ Tests"
case "${1:---fast}" in
  --fast) ./mvnw -q test ;;
  --full) ./mvnw -q verify ;;
esac
# S12: + gitleaks + semgrep
# S14: + PIT (--full)
# S17: + ArchUnit
