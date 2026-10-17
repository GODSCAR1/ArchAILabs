#!/usr/bin/env bash
# scripts/spec-trace.sh — cada escenario de openspec/specs debe tener un test con
# @DisplayName("<nombre del escenario>") en src/test.
set -uo pipefail
[[ -d openspec/specs ]] || { echo "Spec-trace: sin specs todavía."; exit 0; }
missing=0
while IFS= read -r file; do
  cap=${file#openspec/specs/}; cap=${cap%/spec.md}
  while IFS= read -r scenario; do
    scenario=${scenario%$'\r'}
    if ! grep -rqF "@DisplayName(\"$scenario\")" src/test; then
      echo "Sin test: [$cap] Scenario: $scenario"
      missing=$((missing + 1))
    fi
  done < <(sed -n 's/^#### Scenario: *//p' "$file")
done < <(find openspec/specs -name spec.md | sort)

if (( missing > 0 )); then
  echo "Spec-trace: $missing escenario(s) sin test. Añade un test con @DisplayName(\"<escenario>\")." >&2
  exit 1
fi
echo "Spec-trace: todos los escenarios tienen test."
