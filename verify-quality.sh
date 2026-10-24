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
