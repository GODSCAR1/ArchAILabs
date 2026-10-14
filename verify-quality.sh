#!/usr/bin/env bash
# verify-quality.sh  —  uso: verify-quality.sh --fast | --full
set -euo pipefail
./mvnw -q spotless:check
./mvnw -q test
# S05: + openspec validate + spec-trace
# S06: + checkstyle:check
# S12: + gitleaks + semgrep
# S14: + PIT (--full)
# S17: + ArchUnit