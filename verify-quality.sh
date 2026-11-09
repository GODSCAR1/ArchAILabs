#!/usr/bin/env bash
# verify-quality.sh  —  uso: verify-quality.sh --fast | --full
set -euo pipefail
case "${1:---fast}" in
  --fast|--full) ;;
  *) echo "uso: verify-quality.sh --fast | --full" >&2; exit 64 ;;
esac
export OPENSPEC_TELEMETRY=0
echo "▶ Specs"
openspec validate --all --strict --no-interactive
openspec validate --archived --no-interactive
bash scripts/spec-trace.sh
echo "▶ Configuración JPA (ADR docs/decisiones/listado-de-pedidos.md)"
# Segunda capa: no depende de que el guard de la zona protegida haya funcionado (lab S08, prueba 4).
JPA_PROPS=src/main/resources/application.properties
tr -d '\r' < "$JPA_PROPS" | grep -qx 'spring.jpa.open-in-view=false' \
  || { echo "AC/DC: spring.jpa.open-in-view debe ser false en $JPA_PROPS. Carga lo necesario en la consulta del caso de uso; no reactives OSIV." >&2; exit 1; }
tr -d '\r' < "$JPA_PROPS" | grep -qx 'spring.jpa.properties.hibernate.default_batch_fetch_size=50' \
  || { echo "AC/DC: default_batch_fetch_size=50 es la red de seguridad del proyecto ($JPA_PROPS); no lo cambies." >&2; exit 1; }
if grep -rqs 'enable_lazy_load_no_trans' src/main/resources; then
  echo "AC/DC: hibernate.enable_lazy_load_no_trans está prohibido (abre una sesión por acceso: N+1 garantizado)." >&2; exit 1
fi
echo "▶ Catálogo Mongo (ADR docs/adr/0009-catalogo-en-mongodb.md)"
# Segunda capa (lab S10, paso 8): no depende de que el guard de la zona protegida haya funcionado.
tr -d '\r' < "$JPA_PROPS" | grep -qx 'spring.data.mongodb.auto-index-creation=false' \
  || { echo "AC/DC: spring.data.mongodb.auto-index-creation debe ser false: todo índice se declara en CatalogIndexInitializer." >&2; exit 1; }
SCHEMA_VALIDATOR=src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/out/mongo/CatalogSchemaValidator.java
[[ -f "$SCHEMA_VALIDATOR" ]] \
  || { echo "AC/DC: falta $SCHEMA_VALIDATOR (validador \$jsonSchema). Restáuralo; solo una persona puede retirarlo." >&2; exit 1; }
MONGO_BAD=$(grep -rnE 'Document\.class|getCollection\(|,[[:space:]]*"(catalog_products|reviews)"' src/main/java/com/codeja/master_acdc/catalog --include=*.java | grep -v 'CatalogSchemaValidator.java' || true)
if [[ -n "$MONGO_BAD" ]]; then
  echo "AC/DC: consultas o agregaciones del catálogo con el nombre de la colección o Document.class. Usa la clase de entrada (CatalogProduct.class, CatalogReview.class) y una proyección si solo necesitas campos:" >&2
  echo "$MONGO_BAD" >&2; exit 1
fi
echo "▶ Cache multinivel (ADR docs/adr/0010-cache-multinivel.md)"
# Segunda capa (lab S11, paso 8): no depende de que el guard de la zona protegida haya funcionado.
CACHE_DIR=src/main/java/com/codeja/master_acdc/catalog/infrastructure/adapters/out/cache
for f in CacheTtlPolicy.java ProductDetailCache.java; do
  [[ -f "$CACHE_DIR/$f" ]] \
    || { echo "AC/DC: falta $CACHE_DIR/$f (ADR 0010, docs/caching.md). Restáuralo; solo una persona lo retira." >&2; exit 1; }
done
# Analiza cada @Cacheable completo (aunque el formateador lo parta en varias líneas).
CACHEABLE_BAD=$(find src/main/java -name '*.java' -print0 | xargs -0 perl -0777 -ne '
  while (/\@Cacheable(\((?:[^()]++|(?1))*\))/g) {
    my $a = $1;
    print "$ARGV: sin sync = true\n"            unless $a =~ /sync\s*=\s*true/;
    print "$ARGV: unless o varias caches\n"     if $a =~ /unless|cacheNames\s*=\s*\{/;
  }' || true)
[[ -z "$CACHEABLE_BAD" ]] \
  || { echo "AC/DC: todo @Cacheable lleva sync = true, sin unless ni varias caches (protección contra stampede, docs/caching.md):" >&2; echo "$CACHEABLE_BAD" >&2; exit 1; }
echo "▶ Seguridad (ADR docs/adr/0011-autenticacion-refresh-y-version-de-sesion.md)"
# Segunda capa (lab S12, paso 8): no depende de que el guard de la zona protegida haya funcionado.
SEC_DIR=src/main/java/com/codeja/master_acdc/identity
for f in infrastructure/config/HttpSecurityConfiguration.java \
         infrastructure/adapters/out/security/JwtTokenAdapter.java \
         infrastructure/adapters/out/security/JwtDecoderFactory.java \
         infrastructure/adapters/out/security/SessionVersionValidator.java \
         domain/model/Role.java; do
  [[ -f "$SEC_DIR/$f" ]] \
    || { echo "AC/DC: falta $SEC_DIR/$f (ADR 0011). Restáuralo; solo una persona lo retira." >&2; exit 1; }
done
SEC_BAD=$(grep -rnE 'anyRequest\(\)\s*\.permitAll\(\)|requestMatchers\("/(api/)?\*\*"\)\s*\.permitAll|WebSecurityConfigurerAdapter|authorizeRequests\(|antMatchers\(|EnableGlobalMethodSecurity|NoOpPasswordEncoder' src/main/java || true)
[[ -z "$SEC_BAD" ]] \
  || { echo "AC/DC: apertura amplia o API eliminada de Spring Security 7 (AGENTS.md, Seguridad):" >&2; echo "$SEC_BAD" >&2; exit 1; }
CUSTOMER_PARAM=$(grep -rnE '"X-Customer-Id"|@(RequestParam|RequestHeader|PathVariable)\([^)]*customerId' src/main/java/com/codeja/master_acdc/ordering || true)
[[ -z "$CUSTOMER_PARAM" ]] \
  || { echo "AC/DC: la API de pedidos no recibe customerId: el cliente sale del sub del token (ADR 0011):" >&2; echo "$CUSTOMER_PARAM" >&2; exit 1; }
echo "▶ Tests guardianes (lab S09: scripts/protected-tests.txt)"
# Segunda capa: si un guardián desaparece por una vía que ningún hook vio, Verify falla igual.
GUARDS_MISSING=0
while IFS= read -r guard; do
  guard=${guard%$'\r'}
  [[ -z "$guard" || "$guard" == \#* ]] && continue
  if [[ ! -f "$guard" ]]; then
    echo "AC/DC: falta el test guardián $guard. Restáuralo (git restore); solo una persona puede retirarlo de scripts/protected-tests.txt." >&2
    GUARDS_MISSING=1
  fi
done < scripts/protected-tests.txt
(( GUARDS_MISSING == 0 )) || exit 1
echo "▶ Capa 1: formato y estilo"
./mvnw -q spotless:check
./mvnw -q checkstyle:check
echo "▶ Tests"
case "${1:---fast}" in
  --fast)
    ./mvnw -q test
    # Lab S12: el flujo real de autenticación (Testcontainers) también corre en el loop rápido.
    ./mvnw -q -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AuthFlowIT verify ;;
  --full) ./mvnw -q verify ;;
esac
# La matriz de seguridad debe haberse ejecutado entera (anónimo + 5 roles por endpoint).
MATRIX_REPORT=target/surefire-reports/TEST-com.codeja.master_acdc.identity.SecurityMatrixTest.xml
MATRIX_RUN=$(grep -o 'tests="[0-9]*"' "$MATRIX_REPORT" 2>/dev/null | head -1 | tr -dc '0-9')
(( ${MATRIX_RUN:-0} >= 90 )) \
  || { echo "AC/DC: SecurityMatrixTest no se ejecutó completo (${MATRIX_RUN:-0} casos, mínimo 90). No la excluyas ni la recortes." >&2; exit 1; }
# S12: + gitleaks + semgrep
# S14: + PIT (--full)
# S17: + ArchUnit
