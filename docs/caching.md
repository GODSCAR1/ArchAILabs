# Caches del sistema

Fuente única de verdad de las caches. **Toda cache nueva añade aquí su fila antes de escribir código**
(lo exige AGENTS.md). Si una cache no está en esta tabla, no existe.

## Requisito de negocio

- Un cambio en la ficha (precio, nombre, stock mostrado) puede tardar **como máximo 30 s** en verse en
  todas las instancias.
- El **checkout nunca lee de una cache**. En este repo el checkout es `ordering`: reserva stock contra
  `inventory` (`StockReserver`) y no lee del catálogo. Debe seguir así: `ordering` e `inventory` no
  dependen de la cache ni del caso de uso cacheado de la ficha.
- El **stock no se cachea como dato de decisión**: la ficha muestra `stock` (copia de
  `availableStock`), pero nadie reserva ni valida contra ese valor (AGENTS.md, catálogo en MongoDB).

## Tabla

| Cache | Clave | L1 (TTL / tamaño) | L2 (TTL) | Estrategia | Quién invalida |
|---|---|---|---|---|---|
| `products` | `sku` (explícita, `#sku`) | 15 s ±10% / `catalog.cache.local.maximum-size` (default 10.000; `0` desactiva retención L1) | 10 min ±10% | Cache-aside | 1) Edición de producto: evento `ProductChanged(sku)` tras el commit (`AFTER_COMMIT`). 2) `CatalogStockProjectionJob`: los SKU cuyo `availableStock` cambió en la pasada. Cada invalidación: evict L2 + L1 local + mensaje a las demás L1. |
| `categories` | `"tree"` | 5 min, `refreshAfterWrite` | 1 h | Read-through | **Reservada:** el repo no tiene lectura del árbol de categorías; no se implementa en este lab. |
| identity session version (control de seguridad) | `shop:identity:session-version:v1:<UUID>` | ninguna | Redis sin expiración | Un GET por autenticación con clave presente; miss con lock de cuenta, relectura Redis y SETNX desde PostgreSQL | Cambio de contraseña y block publican una barrera monótona antes del commit; miss publica la versión persistente bajo el mismo lock, sin sobrescribir barreras. |

- Valor cacheado: el record inmutable `ProductDetail` (listas y mapas con `copyOf`), nunca un documento
  de Mongo (`CatalogProduct`) ni un objeto mutable.
- SKU inexistente: **no se cachea** (`disableCachingNullValues`); el 404 (`ProductNotFoundException`) se
  lanza sin escribir en la cache. Por eso crear un producto no necesita invalidación.
- Las reseñas paginadas (`ProductReviewsUseCase`) **no** se cachean.

## Justificación de los TTL

- **TTL de L1 (15 s ±10% → máx. 16,5 s) < 30 s.** La invalidación entre instancias va por Redis pub/sub,
  que entrega *como mucho una vez*: si una instancia está reconectando, el mensaje se pierde y nadie lo
  reenvía. En ese caso la L1 se corrige sola al caducar, dentro del límite de 30 s.
- **TTL de L2 (10 min).** La L2 se invalida de forma explícita en cada cambio (edición y stock); su TTL
  solo limita la memoria y el daño si un evict de L2 falla (riesgo en el ADR 0010).
- **Jitter ±10%** en ambos niveles: evita que las claves cargadas a la vez (arranque, campaña) caduquen a
  la vez y provoquen un stampede.
- **Tamaño 10.000:** con ~500 SKU calientes todo cabe en L1; el hit ratio de L1 lo limita el TTL, no el
  tamaño.

## Reglas para cualquier cache

Estas reglas corresponden a caches de datos. La excepción de identidad aprobada y recogida en
AGENTS.md se detalla a continuación.

1. Clave explícita (`key = "#..."`), nunca la generada por defecto.
2. Solo records inmutables como valor.
3. TTL con jitter en L1 y L2.
4. `sync = true` en `@Cacheable` (sin `unless` ni varias caches en la misma anotación).
5. `@Cacheable` y todo lo de cache solo en `infrastructure`: `domain` y `application` no dependen de
   Spring (ArchUnit ya lo exige).
6. Redis caído = miss (se registra), nunca un 5xx.
7. Cambiar la forma de un valor cacheado → subir la versión del prefijo (`shop:cache:v1:` → `v2`).
8. Toda cache en esta tabla, con tests de hit e invalidación.

## Excepción aprobada: versión de sesión de identidad

Es un control de seguridad separado de las caches del ADR 0010. Su valor wire es un entero decimal
canónico no negativo representable como `long`, representado en dominio por un record inmutable de
versión; no contiene cuenta, roles ni secretos. No usa `ProductDetail`, L1, TTL, jitter,
`@Cacheable`, el manejador de errores de catálogo ni invalidación `AFTER_COMMIT`. El prefijo queda
fuera de los barridos de `shop:cache:v1:`.

- Timeout de conexión/comando Redis: **200 ms por operación**, con fallo rápido incluso si
  `catalog.cache.enabled=false`. El miss mantiene el lock de cuenta acotado a 2 s; 200 ms no es un
  límite total de reconstrucción.
- Hit: un GET y comparación exacta. Miss: transacción y lock de cuenta, segunda lectura Redis y
  SETNX de la versión persistida si sigue ausente. Si otro escritor pobló, usar ese valor sin
  sobrescribirlo. Cuenta ausente, fallo de origen/lock, GET/SETNX fallido o valor corrupto rechazan
  la autenticación con **401**, sin fallback, y WARN saneado sin tokens, UUID, claves ni datos personales.
- Contraseña y block: guardar versión incrementada y revocar familias en la misma transacción;
  publicar una barrera atómica que solo avance antes del commit. Un fallo de publicación o una
  confirmación de almacenamiento indisponible produce **503 `session-version-unavailable`** con
  `Retry-After: 1`; nunca compensar Redis a la baja, aunque PostgreSQL revierta.
- Retención sin eviction y lectura del primario. Recuperación vacía coordinada: detener tráfico,
  cerrar conexiones/comandos anteriores, vaciar el namespace y reabrir para reconstruir desde BD;
  no servir snapshots ni réplicas antiguas. La confirmación operativa corresponde a la persona.

Contrato documental previo al código; la evidencia funcional corresponderá a
`SessionVersionValidatorTest`, `SessionVersionRedisIT` y `SessionVersionRevocationIT`.
