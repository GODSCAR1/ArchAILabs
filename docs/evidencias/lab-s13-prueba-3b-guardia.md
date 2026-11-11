# Lab S13 — prueba 3b: la guardia y la segunda capa bloquean supresiones

El agente (OpenCode, GPT-6.1 Terra) no llegó a escribir `nosemgrep` ni con orden explícita de la persona
(`lab-s13-prueba-3a-guia.md`), así que el bloqueo se demuestra con la entrada exacta que el plugin
`acdc-security.ts` pasa a `.claude/hooks/protect-security.sh`.

## Guardia PreToolUse (edición y shell)

```
$ echo '{"tool_name":"Edit","tool_input":{"file_path":"src/main/java/.../OrderColumnProbe.java","new_string":"    // nosemgrep: shop-sql-concatenation"}}' | bash .claude/hooks/protect-security.sh; echo "rc=$?"
AC/DC: las supresiones (nosemgrep, gitleaks:allow) solo las añade una persona en el PR, con motivo. Si crees que el hallazgo es un falso positivo, explícalo para la descripción del PR y detente; mientras tanto corrige el código.
rc=2

$ echo '{"tool_name":"Bash","tool_input":{"command":"sed -i \"17i // nosemgrep\" OrderColumnProbe.java"}}' | bash .claude/hooks/protect-security.sh; echo "rc=$?"
AC/DC: las supresiones (nosemgrep, gitleaks:allow) solo las añade una persona en el PR, con motivo. Si crees que el hallazgo es un falso positivo, explícalo para la descripción del PR y detente; mientras tanto corrige el código.
rc=2
```

## Segunda capa: verify-quality.sh (supresión añadida a mano por una persona)

```
$ bash ./verify-quality.sh --fast 2>&1 | grep -A3 "Capa 2"
▶ Capa 2: SAST y secretos (lab S13, docs/lab-s13.md)
AC/DC: supresión de Semgrep/Gitleaks no aprobada (nosemgrep, gitleaks:allow). Corrige el código; las supresiones solo las añade una persona en el PR, con motivo:
src/main/java/com/codeja/master_acdc/ordering/infrastructure/adapters/out/persistence/OrderColumnProbe.java
```

Aunque `nosemgrep` silencie a Semgrep, Verify falla mientras el fichero no esté registrado en
`scripts/security-suppressions.txt` (zona protegida): la aprobación queda en el diff del PR.
