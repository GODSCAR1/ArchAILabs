# Lab S11 — Cache multinivel (Caffeine + Redis) en la ficha de producto

Rama `lab/s11`. Decisiones en [ADR 0010](adr/0010-cache-multinivel.md); tabla de caches en
[caching.md](caching.md). Agente de implementación: OpenCode (GPT-6 Luna); revisión: Claude.

## Change de OpenSpec

| Change | Qué hace |
|---|---|
| `add-two-level-product-cache` | L1 Caffeine + L2 Redis detrás de `@Cacheable(sync = true)` en un decorador de infraestructura; invalidación `AFTER_COMMIT` + pub/sub entre instancias; job de stock registrado y con invalidación selectiva; `CacheErrorHandler`; ArchUnit del checkout |

Revisión de la propuesta: 4 rondas (9 → 4 → 1 → 1 hallazgos). Revisión del apply: 7 hallazgos
(regla ArchUnit que no cubría el puerto cacheado, `SerializationException` → 500, L1/L2 expuestas en
el `CacheManager` con métricas duplicadas, falta de `CacheErrorHandler`, deriva design/código,
carrera de carga en vuelo sin documentar, umbral de 2 s en vez de 1 s).

Durante el apply el agente **se detuvo** en lugar de saltarse la regla `IllegalCatch` (el contrato
`Cache.get(key, Callable)` parecía obligar a capturar `Exception`). Solución sin excepción a la regla:
la cache compuesta nunca llama a `loader.call()`; delega en `NoOpCache.get(...)` y aplana la
`ValueRetrievalException` doblemente envuelta para que el 404 llegue intacto.

## Bugs del lab S10 destapados en este lab

| Bug | Cómo se encontró | Arreglo |
|---|---|---|
| `CatalogStockProjectionJob` no estaba registrado como bean ni había `@EnableScheduling`: el stock del catálogo no se refrescaba nunca en la aplicación | Revisión de la propuesta (ronda 1) | Registro condicional (`catalog.stock-sync.enabled`) y scheduling en este change |
| La aplicación no configuraba `uuidRepresentation` de MongoDB: `POST /api/products` daba 500 | Paso 7, al sembrar los SKU con k6 | `spring.mongodb.representation.uuid=standard` en `application.properties` |

Ambos pasaban en verde en los tests porque los tests montaban su propio entorno (invocación manual
del job; `MongoClientSettingsBuilderCustomizer` de test). Lección: un test con configuración propia
puede dar verde sin que la aplicación real funcione.

## Medición (paso 7)

k6, 2 minutos, 50 VUs constantes, 500 SKU con distribución sesgada (100 SKU = 20% reciben el 80% de
las peticiones). Una instancia de la aplicación; PostgreSQL, MongoDB 7 y Redis 7 en Docker; todo en
la misma máquina. Redis vaciado (`FLUSHALL`) antes de cada configuración. Hit ratio desde
`cache.gets` de Actuator; CPU de MongoDB con `docker stats` cada 5 s (media de toda la ejecución, arranque incluido;
100% = un núcleo; en régimen estable: ~193% sin cache y ~0,5% con cache).
Script: `bench/k6-product-detail.js`, runner: `bench/run-bench.sh`.

| Configuración | p50 | p99 | Peticiones/s | CPU de MongoDB | Hit ratio L1 / L2 |
|---|---|---|---|---|---|
| Sin cache | 5,74 ms | 13,60 ms | 8.001 | 184,9% | — |
| Solo Redis (`catalog.cache.local.maximum-size=0`) | 2,11 ms | 4,81 ms | 21.081 | 1,8% | 0% / 100,0% |
| L1 + L2 | 0,53 ms | 3,52 ms | 56.654 | 1,6% | 99,9% / 88,1% |

Los `http_req_failed` (4, 501 y 500) son los `POST` del `setup()` que devuelven 409 porque el SKU ya
existía de la ejecución anterior; todas las lecturas de la ficha respondieron 200 (`checks` 100%).

### Conclusiones

- **Redis quita la carga de MongoDB**: de ~185% de CPU de media (~193% en régimen, casi dos núcleos) a menos del 2%, y
  multiplica por 2,6 el throughput. Ese es el problema del enunciado (Mongo al 80% en campaña).
- **La L1 aporta latencia y throughput, no alivio de Mongo**: frente a solo Redis, p50 ÷4
  (2,11 → 0,53 ms) y throughput ×2,7, porque evita el salto de red y la deserialización JSON en el
  99,9% de las lecturas. Con una sola instancia, la CPU de Mongo es la misma en las dos
  configuraciones con cache: Mongo ya solo atiende la carga inicial.
- **El hit ratio de L1 lo limita el TTL, no el tamaño** (500 SKU caben de sobra en 10.000): cada SKU
  caduca cada ~15 s, es decir, ~500 recargas cada 15 s → ~4.000 lecturas de L2 en 2 minutos, que
  coincide con las 4.194 medidas. De ellas, 500 son miss de L2 (la primera carga de cada SKU tras
  el `FLUSHALL`), lo que da el 88,1%.
- Hay menos lecturas de L2 (4.194) que miss de L1 (6.964): con `sync = true` las peticiones
  concurrentes del mismo SKU esperan una única carga, que es la protección contra stampede.

### Limitaciones

- Una sola instancia: no se observa el ahorro de L2 entre réplicas (con 3 réplicas sin L2, cada una
  cargaría cada SKU desde Mongo al caducar su L1).
- k6, la aplicación y las bases de datos comparten CPU; en L1 + L2 el cliente de carga probablemente
  es el cuello de botella, así que 56.654 peticiones/s es una cota inferior.
- El `max` de L1 + L2 (558 ms) corresponde al arranque en frío (primeras cargas y JIT).

## Protecciones (paso 8)

_Pendiente._

## Pruebas con agentes (paso 9)

_Pendiente._
