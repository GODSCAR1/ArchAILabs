# ADR 0010 — Cache multinivel (Caffeine + Redis) para la ficha de producto

- Estado: Aceptado
- Fecha: 2026-11-03
- Relacionado: ADR 0009 (catálogo en MongoDB), `docs/caching.md`, `docs/catalog-model.md`

## Contexto

`GET /api/products/{sku}` concentra el 70% del tráfico de lectura. Tras el ADR 0009 la ficha se resuelve
en una operación de MongoDB, pero con tres réplicas y picos de campaña MongoDB llega al 80% de CPU. El
negocio acepta hasta 30 s de obsolescencia de la ficha, salvo en el checkout (`ordering`), que no lee del
catálogo ni de ninguna cache.

La ficha (`ProductDetail`) incluye `stock`, una copia de `inventory` que `CatalogStockProjectionJob`
refresca cada minuto; por eso la cache también debe invalidarse cuando cambia el stock.

## Decisión

Cache de dos niveles detrás de la abstracción de Spring (`@Cacheable(sync = true)`), aplicada con un
decorador en `catalog.infrastructure` sobre `ProductDetailUseCase` (domain y application siguen sin
Spring):

- **L1: Caffeine** en cada instancia, TTL 15 s ±10%, máx. 10.000 entradas, `recordStats()`.
- **L2: Redis** compartido, TTL 10 min ±10%, serializador JSON tipado por cache, prefijo
  `shop:cache:v1:`, `clear` con SCAN, sin valores nulos.
- **Invalidación:** la edición publica `ProductChanged(sku)` y un `@TransactionalEventListener(AFTER_COMMIT)`
  hace evict en L2 y en la L1 local y publica `cache|key|instanceId` en `shop:cache:evictions`; el job de
  stock invalida solo los SKU cuyo stock cambió. Cada instancia invalida su L1 al recibir el mensaje e
  ignora los suyos. Un único mecanismo transaccional: el listener `AFTER_COMMIT` (no se usa
  `transactionAware()` solo en L2, para no desfasar L1 y L2).
- **Fallos:** `CacheErrorHandler` y la propia cache compuesta tratan los errores de Redis como miss;
  timeout de Redis 200 ms y comandos rechazados mientras está desconectado (fallo rápido).
- **Checkout:** `ordering` e `inventory` no dependen de la cache ni del caso de uso cacheado (ArchUnit).

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Solo Redis | Cada lectura sigue pagando un salto de red y deserialización; Redis pasa a ser el nuevo cuello de botella en picos y un punto único de latencia. |
| Solo Caffeine con TTL corto | Sin nivel compartido, cada réplica carga cada SKU por su cuenta: con N réplicas, N cargas a MongoDB por caducidad. Sin invalidación explícita, la obsolescencia es siempre el TTL entero. |
| Invalidar L1 solo por TTL (sin pub/sub) | Cumple los 30 s, pero todo cambio tarda ~15 s en verse aunque nada falle. |
| Sacar `stock` del valor cacheado | Obligaría a una segunda lectura por ficha y rompería la regla de "la ficha en 1 operación". |
| Que el job de stock invalide todos los SKU en cada pasada | Vaciaría la L2 cada minuto y el hit ratio de L2 caería a casi cero. |

## Consecuencias y riesgos aceptados

- **Pérdida de mensajes de pub/sub:** acotada por el TTL de L1 (< 30 s). Es la razón del TTL de L1.
- **Evict de L2 fallido con Redis inestable:** la L2 podría quedarse con el valor viejo hasta su TTL
  (10 min) y repoblar las L1. Mitigación: reintento del evict y registro del error. *Riesgo abierto:
  valorar bajar el TTL de L2 o un evict diferido; pendiente de revisar con Ricardo.*
- **Stock mostrado:** puede llegar con el retraso del job (1 min) más la propagación; es un dato
  informativo, nadie reserva contra él.
- Dos niveles más que operar (métricas `cache.gets` de L1 y L2 en Actuator).
- Cambios en `ProductDetail` exigen subir la versión del prefijo.
