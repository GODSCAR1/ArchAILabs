#!/usr/bin/env bash
# scripts/spec-trace.sh — cada escenario de openspec/specs debe tener un test con
# @DisplayName("<nombre del escenario>") en src/test.
# Excepción (lab S12): los escenarios de un requirement que un change ACTIVO declara en
# "## REMOVED Requirements" para esa misma capability no se exigen; su comportamiento ya no existe
# en el código y el requirement desaparecerá del spec principal al archivar el change.
set -uo pipefail
[[ -d openspec/specs ]] || { echo "Spec-trace: sin specs todavía."; exit 0; }

# "capability|requirement" eliminados por changes activos (no archivados).
removed=$(
  find openspec/changes -path openspec/changes/archive -prune -o -name spec.md -print 2>/dev/null |
    while IFS= read -r delta; do
      cap=${delta#openspec/changes/*/specs/}; cap=${cap%/spec.md}
      awk -v cap="$cap" '
        { sub(/\r$/, "") }
        /^## / { inRemoved = ($0 ~ /^## REMOVED Requirements/) }
        inRemoved && /^### Requirement: / { sub(/^### Requirement: */, ""); print cap "|" $0 }
      ' "$delta"
    done
)

missing=0
skipped=0
while IFS= read -r file; do
  cap=${file#openspec/specs/}; cap=${cap%/spec.md}
  while IFS=$'\t' read -r requirement scenario; do
    if grep -qxF "$cap|$requirement" <<<"$removed"; then
      skipped=$((skipped + 1))
      continue
    fi
    if ! grep -rqF "@DisplayName(\"$scenario\")" src/test; then
      echo "Sin test: [$cap] Scenario: $scenario"
      missing=$((missing + 1))
    fi
  done < <(awk '
    { sub(/\r$/, "") }
    /^### Requirement: / { req = $0; sub(/^### Requirement: */, "", req) }
    /^#### Scenario: / { sc = $0; sub(/^#### Scenario: */, "", sc); print req "\t" sc }
  ' "$file")
done < <(find openspec/specs -name spec.md | sort)

(( skipped > 0 )) && echo "Spec-trace: $skipped escenario(s) omitidos por requirements REMOVED en changes activos."
if (( missing > 0 )); then
  echo "Spec-trace: $missing escenario(s) sin test. Añade un test con @DisplayName(\"<escenario>\")." >&2
  exit 1
fi
echo "Spec-trace: todos los escenarios tienen test."
