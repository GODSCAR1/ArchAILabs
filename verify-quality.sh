#!/usr/bin/env bash
# verify-quality.sh  —  uso: verify-quality.sh --fast | --full
set -euo pipefail
./mvnw -q spotless:check
case "${1:---fast}" in
  --fast) ./mvnw -q test ;;
  --full) ./mvnw -q verify ;;
  *) echo "uso: verify-quality.sh --fast | --full" >&2; exit 64 ;;
esac
# S05: + openspec validate + spec-trace
# S06: + checkstyle:check
# S12: + gitleaks + semgrep
# S14: + PIT (--full)
# S17: + ArchUnit
