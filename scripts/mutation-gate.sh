#!/usr/bin/env bash
# scripts/mutation-gate.sh — capa 3 de AC/DC Verify: mutation testing de pricing (lab S15, docs/lab-s15.md).
# Lo usan verify-quality.sh (local, hook Stop, plugin de OpenCode) y el job mutation-testing de CI.
set -euo pipefail
# Suelo propio (segunda capa): aunque alguien baje pit.mutationThreshold en pom.xml, el gate no lo acepta.
PIT_FLOOR=93
PIT_MIN=$(tr -d '\r' < pom.xml | sed -n 's:.*<pit.mutationThreshold>\([0-9]*\)</pit.mutationThreshold>.*:\1:p' | head -1)
(( ${PIT_MIN:-0} >= PIT_FLOOR )) \
  && tr -d '\r' < pom.xml | grep -q '<mutationThreshold>${pit.mutationThreshold}</mutationThreshold>' \
  || { echo "AC/DC: el umbral de PIT de pricing en pom.xml (${PIT_MIN:-ausente}) está por debajo de $PIT_FLOOR o desconectado de la execution pit-pricing. No se baja: restáuralo (docs/lab-s15.md, paso 7)." >&2; exit 1; }
if grep -qsE 'mutationThreshold|pitest|mutators' <<<"${MAVEN_OPTS:-} ${MAVEN_ARGS:-} $(cat .mvn/maven.config 2>/dev/null)"; then
  echo "AC/DC: MAVEN_OPTS, MAVEN_ARGS o .mvn/maven.config tocan la configuración de PIT. El umbral de mutation testing no se cambia por entorno." >&2; exit 1
fi
if ! PIT_OUT=$(./mvnw -B -ntp test-compile org.pitest:pitest-maven:mutationCoverage@pit-pricing 2>&1); then
  bash scripts/pit-survivors.sh >/dev/null 2>&1 || true
  grep -aE '^>> (Generated|Line Coverage)|did not pass the mutation|threshold' <<<"$PIT_OUT" >&2 || true
  echo "AC/DC: el mutation score de pricing está por debajo de ${PIT_MIN}% (o PIT falló). Supervivientes en .acdc/pit-survivors.md: escribe tests de la REGLA que revela cada uno (resultado observable, sin Mockito en pricing, sin verify). Si no es una regla, clasifícalo en docs/lab-s15.md (sobrante, equivalente o defensivo). El umbral no se baja." >&2
  exit 1
fi
bash scripts/pit-survivors.sh >/dev/null
grep -aE '^>> Generated [0-9]+ mutations' <<<"$PIT_OUT" | sed 's/^/  pricing: /' || true
