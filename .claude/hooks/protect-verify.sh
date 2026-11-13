#!/usr/bin/env bash
# .claude/hooks/protect-verify.sh — PreToolUse (Edit|MultiEdit|Write|Bash|PowerShell): zona protegida AC/DC
# Falla CERRADO: si no puede verificar, bloquea.
command -v jq >/dev/null || { echo "AC/DC: falta jq; no puedo verificar la zona protegida." >&2; exit 2; }

ROOT="${CLAUDE_PROJECT_DIR:-$PWD}"
command -v cygpath >/dev/null && ROOT=$(cygpath -u "$ROOT")
ROOT="${ROOT//\\//}"
LIST="$ROOT/scripts/protected-tests.txt"
[[ -f "$LIST" ]] || { echo "AC/DC: falta scripts/protected-tests.txt; no puedo verificar los tests guardianes." >&2; exit 2; }
mapfile -t GUARDS < <(tr -d '\r' < "$LIST" | grep -vE '^[[:space:]]*(#|$)')

INPUT=$(cat)
TOOL=$(jq -r '.tool_name // empty' <<<"$INPUT")

guard_msg() {
  echo "AC/DC: $1 es un test guardián (scripts/protected-tests.txt): protege el buscador o la concurrencia. No se borra, no se mueve, no se renombra ni se debilita. Si crees que sobra o es lento, explica por qué a la persona y detente." >&2
}

# Shell: bloquear comandos que escriben, mueven o borran un test guardián.
if [[ "$TOOL" == "Bash" || "$TOOL" == "PowerShell" ]]; then
  CMD=$(jq -r '.tool_input.command // empty' <<<"$INPUT")
  WRITES='(>|sed[[:space:]]+-i|(^|[^[:alnum:]_-])(rm|mv|cp|tee|truncate|unlink)[[:space:]]|git[[:space:]]+(rm|mv|checkout|restore)|set-content|add-content|out-file|copy-item|move-item|remove-item|rename-item|new-item|clear-content)'
  if grep -qiE "$WRITES" <<<"$CMD"; then
    for g in "${GUARDS[@]}"; do
      name=$(basename "$g")
      if grep -qF "$name" <<<"$CMD"; then guard_msg "$g"; exit 2; fi
    done
    # Lab S12 (ADR 0011): configuración de seguridad, servicio de tokens y roles.
    for sec in HttpSecurityConfiguration JwtTokenAdapter JwtDecoderFactory SessionVersionValidator identity/domain/model/Role; do
      if grep -qF "$sec" <<<"$CMD"; then
        echo "AC/DC: $sec es zona protegida de seguridad (ADR 0011). No se edita, no se desactiva ni se sortea desde la shell: explica el cambio a la persona y detente." >&2; exit 2
      fi
    done
    # Lab S13: capa 2 (reglas, exclusiones y allowlists de Semgrep/Gitleaks, hallazgos abiertos).
    if grep -qE 'semgrep-rules|\.semgrepignore|\.gitleaks(\.toml|ignore)|gitleaks-baseline|open-findings\.md' <<<"$CMD"; then
      echo "AC/DC: la configuración de la capa 2 (semgrep-rules/, .semgrepignore, .gitleaks.toml, baseline, open-findings.md) es zona protegida. No se edita ni se sortea desde la shell: explica el cambio a la persona y detente." >&2; exit 2
    fi
    # Lab S16b: capa 1 (filtro de SpotBugs, ruleset de PMD, Checkstyle, flags de Error Prone).
    if grep -qE 'spotbugs-exclude|pmd[\\/]+ruleset|checkstyle[^[:space:]]*\.xml|jvm\.config' <<<"$CMD"; then
      echo "AC/DC: la configuración de la capa 1 (config/spotbugs-exclude.xml, config/pmd/ruleset.xml, config/checkstyle*.xml, .mvn/jvm.config) es zona protegida. No se edita ni se sortea desde la shell: corrige el código o explica a la persona por qué la regla no aplica y detente." >&2; exit 2
    fi
    if grep -qF "CatalogSchemaValidator" <<<"$CMD"; then
      echo "AC/DC: CatalogSchemaValidator está en la zona protegida (validador \$jsonSchema del catálogo, ADR 0009). No se edita, no se desactiva ni se sortea: explica el problema a la persona y detente." >&2; exit 2
    fi
  fi
  exit 0
fi

F=$(jq -r '.tool_input.file_path // empty' <<<"$INPUT")
[[ -z "$F" ]] && exit 0

# Normalizar rutas (Windows: C:\... y /c/... → mismo formato)
command -v cygpath >/dev/null && F=$(cygpath -u "$F")
F="${F//\\//}"
REL="${F#"$ROOT"/}"          # ruta relativa a la raíz del proyecto

for g in "${GUARDS[@]}"; do
  if [[ "$REL" == "$g" ]]; then guard_msg "$g"; exit 2; fi
done

# Solo la raíz: config/ de la raíz, no infrastructure/config/
case "$REL" in
  src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/out/mongo/CatalogSchemaValidator.java|\
  src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/out/cache/CacheTtlPolicy.java|\
  src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/out/cache/ProductDetailCache.java|\
  src/main/java/com/codeja/master_acdc/identity/infrastructure/config/HttpSecurityConfiguration.java|\
  src/main/java/com/codeja/master_acdc/identity/infrastructure/adapters/out/security/JwtTokenAdapter.java|\
  src/main/java/com/codeja/master_acdc/identity/infrastructure/adapters/out/security/JwtDecoderFactory.java|\
  src/main/java/com/codeja/master_acdc/identity/infrastructure/adapters/out/security/SessionVersionValidator.java|\
  src/main/java/com/codeja/master_acdc/identity/domain/model/Role.java|\
  config/*|.mvn/*|.claude/*|.opencode/*|.github/*|scripts/*|pom.xml|verify-quality.sh|lefthook.yml|openspec/config.yaml|src/main/resources/application*|.env|.env.*|\
  semgrep-rules/*|.semgrepignore|.gitleaks.toml|.gitleaksignore|gitleaks-baseline.json|.acdc/open-findings.md)
    echo "AC/DC: $REL está en la zona protegida: solo la edita una persona. No la modifiques por ninguna otra vía (shell, scripts, renombrados). Explica el cambio que propones y detente." >&2
    exit 2 ;;
esac
exit 0
