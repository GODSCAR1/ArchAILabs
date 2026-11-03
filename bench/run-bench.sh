#!/usr/bin/env bash
# Lab S11 paso 7: ejecuta las tres configuraciones y guarda resultados en bench/results/.
# Requisitos: docker compose up -d, k6 en el PATH y el jar empaquetado:
#   bash ./mvnw -q -DskipTests package
# Uso: bash bench/run-bench.sh [sin-cache|solo-redis|l1-l2 ...]   (por defecto, las tres)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/bench/results"
JAR="$(ls "$ROOT"/target/master-acdc-*.jar | grep -v plain | head -1)"
PORT=8080
mkdir -p "$OUT"

# k6: PATH, variable K6 o las rutas típicas de winget/scoop en Windows
K6="${K6:-$(command -v k6 || true)}"
for candidate in "/c/Program Files/k6/k6.exe" "$HOME/scoop/shims/k6.exe"; do
  [[ -z "$K6" && -x "$candidate" ]] && K6="$candidate"
done
[[ -n "$K6" ]] || { echo "No encuentro k6: instálalo (winget install k6 --source winget) o exporta K6=/ruta/k6.exe" >&2; exit 1; }

for tool in docker curl java; do
  command -v "$tool" > /dev/null || { echo "Falta '$tool' en el PATH" >&2; exit 1; }
done
grep -rq "catalog.cache.local.maximum-size" "$ROOT/src/main/java" \
  || echo "AVISO: falta catalog.cache.local.maximum-size; 'solo-redis' mediría lo mismo que 'l1-l2'" >&2

APP_PID=""
trap '[[ -n "$APP_PID" ]] && kill "$APP_PID" 2>/dev/null || true' EXIT

COMMON=(
  --server.port=$PORT
  --spring.datasource.url=jdbc:postgresql://localhost:5432/acdc
  --spring.datasource.username=acdc
  --spring.datasource.password=acdc
  --spring.mongodb.uri=mongodb://localhost:27017/acdc
  --spring.data.mongodb.uri=mongodb://localhost:27017/acdc
  --catalog.stock-sync.enabled=false
  --logging.level.root=WARN
)

args_for() {
  case "$1" in
    sin-cache)  echo "--catalog.cache.enabled=false" ;;
    solo-redis) echo "--catalog.cache.enabled=true --catalog.cache.local.maximum-size=0" ;;
    l1-l2)      echo "--catalog.cache.enabled=true" ;;
    *) echo "Configuración desconocida: $1" >&2; exit 1 ;;
  esac
}

run_one() {
  local config="$1"
  echo "=== $config"
  docker exec acdc-redis redis-cli FLUSHALL > /dev/null

  # shellcheck disable=SC2046
  java -jar "$JAR" "${COMMON[@]}" $(args_for "$config") > "$OUT/$config-app.log" 2>&1 &
  local app=$!
  APP_PID=$app

  for _ in $(seq 1 60); do
    curl -fs "http://localhost:$PORT/actuator/health" > /dev/null && break
    sleep 2
  done

  # CPU de MongoDB cada 5 s mientras dura la carga
  ( while kill -0 $app 2>/dev/null; do
      docker stats --no-stream --format '{{.CPUPerc}}' acdc-mongo
      sleep 5
    done ) > "$OUT/$config-mongo-cpu.txt" &
  local sampler=$!

  "$K6" run -e BASE_URL="http://localhost:$PORT" -e CONFIG="$config" -e DURATION="${DURATION:-2m}" \
    --summary-export "$OUT/$config-summary.json" \
    "$ROOT/bench/k6-product-detail.js" 2>&1 | tee "$OUT/$config-k6.txt"

  kill $sampler 2>/dev/null || true
  kill $app 2>/dev/null || true
  wait $app 2>/dev/null || true

  local cpu
  cpu=$(tr -d '%' < "$OUT/$config-mongo-cpu.txt" | awk 'NF {s+=$1; n++} END {if (n) printf "%.1f%%", s/n; else print "—"}')
  echo "[$config] CPU media de MongoDB: $cpu" | tee -a "$OUT/$config-k6.txt"
}

CONFIGS=("$@")
[[ ${#CONFIGS[@]} -eq 0 ]] && CONFIGS=(sin-cache solo-redis l1-l2)
for c in "${CONFIGS[@]}"; do run_one "$c"; done
echo "Resultados en $OUT"
