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
  # Lab S14: el umbral de cobertura no se sortea pasando la propiedad (-D, MAVEN_OPTS) a Maven.
  if grep -qE 'acdc\.coverage' <<<"$CMD"; then
    echo "AC/DC: el umbral de cobertura de ramas (acdc.coverage.* en pom.xml) no se cambia ni se desactiva desde la línea de comandos. Cubre las ramas de regla con tests o explica a la persona por qué una rama no merece test y detente." >&2; exit 2
  fi
  # Lab S15: ni el umbral de PIT ni sus mutadores se pasan por la línea de comandos, y nadie antepone
  # otro PATH a verify-quality.sh (un "semgrep" falso pondría la capa 2 en verde sin analizar).
  if grep -qE 'mutationThreshold|-Dmutators' <<<"$CMD"; then
    echo "AC/DC: el umbral de mutation testing (pit.mutationThreshold en pom.xml) y los mutadores no se cambian desde la línea de comandos. Mide con la execution pit-pricing; si un superviviente no es una regla, clasifícalo en docs/lab-s15.md y detente." >&2; exit 2
  fi
  # Lab S18: la línea base de ArchUnit no se regenera ni se recongela desde la línea de comandos.
  if grep -qiE 'allowStoreCreation|freeze\.refreeze|freeze\.store' <<<"$CMD"; then
    echo "AC/DC: la línea base de ArchUnit (archunit_store/) no se crea, se recongela ni se redirige desde la línea de comandos: solo puede bajar al corregir violaciones. Corrige el diseño como indica el because y, si crees que la regla está mal, explícalo a la persona y detente." >&2; exit 2
  fi
  if grep -qE '(^|[^[:alnum:]_])PATH=.*verify-quality\.sh' <<<"$CMD"; then
    echo "AC/DC: verify-quality.sh se ejecuta con el PATH de la persona. Si una herramienta (semgrep, gitleaks) no arranca, explícalo a la persona y detente; no la sustituyas." >&2; exit 2
  fi
  WRITES='(>|sed[[:space:]]+-i|perl[[:space:]]+-[[:alnum:]]*i|(^|[^[:alnum:]_-])(rm|mv|cp|tee|truncate|unlink)[[:space:]]|git[[:space:]]+(rm|mv|checkout|restore)|set-content|add-content|out-file|copy-item|move-item|remove-item|rename-item|new-item|clear-content)'
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
    # Lab S14 (prueba 4): el build y el loop (umbral de JaCoCo, gate de verify-quality.sh) tampoco se editan desde la shell.
    # Lab S15 (prueba 2): el gate de la capa 3 y la lista de supervivientes aceptados viven en scripts/.
    if grep -qE '(^|[^[:alnum:]_.-])scripts[\\/]' <<<"$CMD"; then
      echo "AC/DC: scripts/ es zona protegida (gates de Verify: mutation-gate.sh, pit-accepted-survivors.txt, tests guardianes, supresiones). No se edita ni se sortea desde la shell: explica el cambio a la persona y detente." >&2; exit 2
    fi
    if grep -qE '(^|[^[:alnum:]_.-])(pom\.xml|verify-quality\.sh|lefthook\.yml)|\.mvn[\\/]' <<<"$CMD"; then
      echo "AC/DC: pom.xml, verify-quality.sh, lefthook.yml y .mvn/ son zona protegida (incluido el umbral de JaCoCo). No se editan ni se sortean desde la shell: explica el cambio a la persona y detente." >&2; exit 2
    fi
    # Lab S16: las migraciones no se escriben desde la shell (las nuevas, con la herramienta de edición).
    if grep -qE 'db[\\/]+migration' <<<"$CMD"; then
      echo "AC/DC: las migraciones de Flyway no se escriben, mueven ni borran desde la shell. Crea las nuevas con la herramienta de edición; las existentes no se tocan (expandir-contraer)." >&2; exit 2
    fi
    # Lab S18: reglas de ArchUnit, su configuración y la deuda congelada (línea base).
    if grep -qE 'archunit|master_acdc[\\/]+arch(itecture)?[\\/]' <<<"$CMD"; then
      echo "AC/DC: las reglas de ArchUnit (src/test/java/com/codeja/master_acdc/arch/ y architecture/), archunit.properties y archunit_store/ son zona protegida (lab S18, ADR 0012). No se relajan, no se congelan de nuevo ni se regenera la línea base: corrige el diseño como indica el because de la regla y, si crees que la regla está mal, explícalo a la persona y detente." >&2; exit 2
    fi
    # Lab S19: los ADR y el mapa de contextos son decisiones humanas (ADR 0013-0015).
    if grep -qE 'docs[\\/]+(adr[\\/]|domain[\\/]+context-map)' <<<"$CMD"; then
      echo "AC/DC: docs/adr/ y docs/domain/context-map.md son zona protegida (lab S19): registran decisiones de arquitectura y de negocio. No se editan desde la shell. Si crees que una decisión debe cambiar, explícalo a la persona y detente." >&2; exit 2
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

# Lab S16: una migración que ya existe no se edita (Flyway guarda su checksum); el cambio va en una nueva.
if [[ "$REL" == src/main/resources/db/migration/* && -e "$ROOT/$REL" ]]; then
  echo "AC/DC: $REL es una migración existente: no se edita. Expresa el cambio en una migración nueva (expandir-contraer: añadir, migrar datos y, en otra versión, retirar)." >&2
  exit 2
fi

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
  src/test/java/com/codeja/master_acdc/arch/*|src/test/java/com/codeja/master_acdc/architecture/*|\
  src/test/resources/archunit.properties|src/test/resources/archunit_store/*|\
  docs/adr/*|docs/domain/context-map.md|\
  config/*|.mvn/*|.claude/*|.opencode/*|.github/*|scripts/*|pom.xml|verify-quality.sh|lefthook.yml|openspec/config.yaml|src/main/resources/application*|.env|.env.*|\
  semgrep-rules/*|.semgrepignore|.gitleaks.toml|.gitleaksignore|gitleaks-baseline.json|.acdc/open-findings.md)
    echo "AC/DC: $REL está en la zona protegida: solo la edita una persona. No la modifiques por ninguna otra vía (shell, scripts, renombrados). Explica el cambio que propones y detente." >&2
    exit 2 ;;
esac
exit 0
