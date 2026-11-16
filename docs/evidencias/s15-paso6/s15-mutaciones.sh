#!/usr/bin/env bash
# Lab S15, paso 6: una mutación manual por regla, DISTINTA de las de PIT. Uso (desde master-acdc):
#   bash ../s15-mutaciones.sh          (las seis)
#   ONLY=5 bash ../s15-mutaciones.sh   (solo la 5)
# Cada mutación: cambia el código, ejecuta los tests de pricing, guarda el informe en
# ../s15-mutacion-N.log y restaura el archivo (también si cortas con Ctrl+C).
set -uo pipefail
[[ -z "$(git status --porcelain --untracked-files=no -- src/main)" ]] || { echo "Haz commit antes: el script restaura src/main con git restore." >&2; exit 1; }
P=src/main/java/com/codeja/master_acdc/pricing
trap 'git restore -- "$P"; echo "restaurado"' EXIT

mutate() {  # n, archivo, perl, patrón que debe quedar tras mutar, descripción
  local n=$1 file=$2 expr=$3 check=$4 what=$5 log="../s15-mutacion-$1.log"
  [[ -z "${ONLY:-}" || "$ONLY" == "$n" ]] || return 0
  perl -0pi -e "$expr" "$file"
  grep -qF -- "$check" "$file" || { echo "Mutación $n no aplicada en $file" >&2; exit 1; }
  echo "== Mutación $n: $what"
  { echo "# Mutación $n: $what"; git -c core.autocrlf=true diff -U0 -- "$file"; } > "$log"
  rm -rf target/surefire-reports
  ./mvnw -B test -Dtest='com/codeja/master_acdc/pricing/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false \
    -Dmaven.test.failure.ignore=true -Djacoco.skip=true \
    -Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip -Dpmd.skip >> "$log" 2>&1
  echo "## Tests que fallan" >> "$log"
  grep -h -A4 '<<< \(FAILURE\|ERROR\)!' target/surefire-reports/*.txt 2>/dev/null >> "$log" \
    || echo "NINGUNO: la regla no está protegida" >> "$log"
  sed -n '/^## Tests que fallan/,$p' "$log" | grep -E '<<<|regla|xpect|but was|NINGUNO|Exception' | cut -c1-220 | head -10
  git restore -- "$file"
}

mutate 1 $P/domain/model/VolumeDiscount.java \
  's/new BigDecimal\("0\.10"\);/new BigDecimal("0.05"); \/\/ MUTACION S15/' \
  '"0.05"); // MUTACION S15' 'descuento por volumen del 5 % en lugar del 10 %'

mutate 2 $P/domain/model/Coupon.java \
  's/return !today\.isAfter\(validUntil\);/return today.isBefore(validUntil); \/\/ MUTACION S15/' \
  'today.isBefore(validUntil); // MUTACION S15' 'el cupón deja de valer el último día (validUntil pasa a ser exclusivo)'

mutate 3 $P/domain/model/OrderPricing.java \
  's/    Money taxable = applyCoupon\(subtotalOf\(order\), coupon\);\n    return taxable\.plus\(taxable\.times\(taxRate\)\)\.amount\(\);/    Money gross = subtotalOf(order).plus(subtotalOf(order).times(taxRate)); \/\/ MUTACION S15\n    return applyCoupon(gross, coupon).amount();/' \
  'Money gross = subtotalOf(order)' 'el cupón se aplica después de impuestos'

mutate 4 $P/domain/model/OrderPricing.java \
  's/      return Money\.ZERO;/      return discounted.times(new BigDecimal("-1")); \/\/ MUTACION S15/' \
  'discounted.times(new BigDecimal("-1")); // MUTACION S15' 'si el cupón supera el subtotal, se cobra la diferencia (valor absoluto) en lugar de 0'

mutate 5 $P/domain/model/Money.java \
  's/RoundingMode\.HALF_EVEN\)/RoundingMode.HALF_UP); \/\/ MUTACION S15/' \
  'HALF_UP); // MUTACION S15' 'redondeo HALF_UP en lugar de HALF_EVEN'

mutate 6 $P/application/CouponService.java \
  's/    if \(coupon\.singleUse\(\)\) \{\n      coupons\.markUsed\(couponCode\);\n    \}\n    return total;/    return total;/; s/    if \(coupon\.singleUse\(\) && coupons\.isUsed\(couponCode\)\) \{/    if (coupon.singleUse()) {\n      coupons.markUsed(couponCode); \/\/ MUTACION S15: se marca antes de comprobar\n    }\n    if (coupon.singleUse() && coupons.isUsed(couponCode)) {/' \
  'MUTACION S15: se marca antes de comprobar' 'el cupón de un solo uso se marca como usado antes de comprobar si ya lo estaba'
echo "Logs: ../s15-mutacion-{1..6}.log"
