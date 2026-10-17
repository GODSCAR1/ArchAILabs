#!/usr/bin/env bash
# scripts/spec-guard.sh <base-ref> — openspec/specs solo cambia al archivar un change revisado.
# Endurecido: cada capacidad modificada en openspec/specs/ debe tener su delta en un change
# archivado EN ESTE MISMO PR (no basta con archivar cualquier change).
set -euo pipefail
BASE=${1:-origin/main}
CHANGED=$(git diff --name-only "$BASE"...HEAD)
ARCHIVED=$(git diff --name-only --diff-filter=A "$BASE"...HEAD | grep '^openspec/changes/archive/' || true)

SPECS=$(grep '^openspec/specs/.*spec\.md$' <<<"$CHANGED" || true)
[[ -z "$SPECS" ]] && { echo "Spec-guard: OK (sin cambios en openspec/specs/)"; exit 0; }

fail=0
while IFS= read -r f; do
  cap=${f#openspec/specs/}; cap=${cap%/spec.md}
  if ! grep -qE "^openspec/changes/archive/[^/]+/specs/${cap}/spec\.md$" <<<"$ARCHIVED"; then
    echo "Spec-guard: '$cap' cambió sin un delta archivado para esa capacidad en este PR." >&2
    fail=1
  fi
done <<<"$SPECS"

if (( fail )); then
  echo "Los cambios de comportamiento entran por openspec/changes/<nombre>/ y 'openspec archive'." >&2
  exit 1
fi
echo "Spec-guard: OK (cada spec modificada viene de un change archivado en este PR)"
