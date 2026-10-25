#!/usr/bin/env bash
# .claude/hooks/protect-verify.sh — PreToolUse (Edit|Write): zona protegida AC/DC
# Falla CERRADO: si no puede verificar, bloquea.
command -v jq >/dev/null || { echo "AC/DC: falta jq; no puedo verificar la zona protegida." >&2; exit 2; }

ROOT="${CLAUDE_PROJECT_DIR:-$PWD}"
F=$(jq -r '.tool_input.file_path // empty')
[[ -z "$F" ]] && exit 0

# Normalizar rutas (Windows: C:\... y /c/... → mismo formato)
if command -v cygpath >/dev/null; then
  F=$(cygpath -u "$F"); ROOT=$(cygpath -u "$ROOT")
fi
F="${F//\\//}"; ROOT="${ROOT//\\//}"
REL="${F#"$ROOT"/}"          # ruta relativa a la raíz del proyecto

# Solo la raíz: config/ de la raíz, no infrastructure/config/
case "$REL" in
  config/*|.claude/*|.opencode/*|.github/*|scripts/*|pom.xml|verify-quality.sh|lefthook.yml|openspec/config.yaml|src/main/resources/application*|.env|.env.*)
    echo "AC/DC: $REL está en la zona protegida: solo la edita una persona. No la modifiques por ninguna otra vía (shell, scripts, renombrados). Explica el cambio que propones y detente." >&2
    exit 2 ;;
esac
exit 0
