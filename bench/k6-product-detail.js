// Lab S11 paso 7: 2 minutos de tráfico contra GET /api/products/{sku}
// 500 SKU con distribución sesgada: el 20% de los SKU (100) recibe el 80% de las peticiones.
//
// k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=l1-l2 bench/k6-product-detail.js
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CONFIG = __ENV.CONFIG || 'sin-config';
const TOTAL_SKUS = 500;
const HOT_SKUS = 100; // 20% de 500

export const options = {
  scenarios: {
    detail: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 50),
      duration: __ENV.DURATION || '2m',
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

const sku = (i) => `BENCH-${String(i).padStart(4, '0')}`;

export function setup() {
  // Crea los 500 SKU si no existen (409 = ya existe, también vale).
  for (let i = 0; i < TOTAL_SKUS; i++) {
    const res = http.post(
      `${BASE_URL}/api/products`,
      JSON.stringify({ name: `Producto bench ${i}`, sku: sku(i), price: 1000 + i }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup' } },
    );
    if (res.status !== 201 && res.status !== 409) {
      throw new Error(`No pude crear ${sku(i)}: HTTP ${res.status} ${res.body}`);
    }
  }
}

export default function () {
  const i = Math.random() < 0.8
    ? Math.floor(Math.random() * HOT_SKUS)
    : HOT_SKUS + Math.floor(Math.random() * (TOTAL_SKUS - HOT_SKUS));
  const res = http.get(`${BASE_URL}/api/products/${sku(i)}`, { tags: { name: 'detail' } });
  check(res, { 'status 200': (r) => r.status === 200 });
}

function counter(cache, result) {
  const res = http.get(
    `${BASE_URL}/actuator/metrics/cache.gets?tag=cache:${cache}&tag=result:${result}`,
  );
  if (res.status !== 200) return 0;
  return res.json().measurements.find((m) => m.statistic === 'COUNT').value;
}

export function teardown() {
  const ratio = (cache) => {
    const hit = counter(cache, 'hit');
    const miss = counter(cache, 'miss');
    return hit + miss === 0 ? '—' : `${((100 * hit) / (hit + miss)).toFixed(1)}% (${hit}/${hit + miss})`;
  };
  console.log(`[${CONFIG}] hit ratio L1: ${ratio('products.l1')} | L2: ${ratio('products.l2')}`);
}
