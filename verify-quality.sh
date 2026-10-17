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
echo "▶ Formato y tests"
./mvnw -q spotless:check
case "${1:---fast}" in
  --fast) ./mvnw -q test ;;
  --full) ./mvnw -q verify ;;
esac
# S06: + checkstyle:check
# S12: + gitleaks + semgrep
# S14: + PIT (--full)
# S17: + ArchUnit
