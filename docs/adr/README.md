# Architecture Decision Records

Una decisión por fichero, numerada y sin reescribir. Para cambiar una decisión aceptada se crea un ADR nuevo
que la sustituye, y en el antiguo solo se cambia el estado a `sustituido por NNNN`. Formato: estado,
contexto con evidencias, decisión verificable (cómo se comprueba), consecuencias (al menos dos negativas) y
alternativas en una línea. Si un ADR ocupa más de una pantalla, la discusión va a un RFC y aquí queda la
decisión.

| ADR | Decisión | Estado | Verificación |
|---|---|---|---|
| [0007](0007-buscador-y-concurrencia-del-catalogo.md) | Buscador y concurrencia del catálogo | aceptado | tests del buscador (lab S09) |
| 0008 | — (número sin usar) | — | — |
| [0009](0009-catalogo-en-mongodb.md) | Catálogo en MongoDB | propuesto | — |
| [0010](0010-cache-multinivel.md) | Caché multinivel (Caffeine + Redis) para la ficha de producto | aceptado | tests de caché (lab S11) |
| [0011](0011-autenticacion-refresh-y-version-de-sesion.md) | Autenticación stateless, refresh tokens rotatorios y versión de sesión | aceptado | tests de seguridad (lab S12) |
| [0012](0012-arquitectura-hexagonal.md) | Arquitectura hexagonal estricta (ordering como referencia) | aceptado | `HexagonalArchitectureTest`, `ModuleBoundariesTest`, `ConventionsTest` |
| [0013](0013-limites-de-los-bounded-contexts.md) | Límites de los bounded contexts, subdominios y shared kernel | aceptado | `ContextMapTest` |
| [0014](0014-integracion-entre-contextos.md) | Consultas por puertos publicados, efectos por eventos, sin tablas ajenas | aceptado | `ContextMapTest`, `TableOwnershipTest`, `ModuleBoundariesTest` |
| [0015](0015-stock-insuficiente-tras-el-cobro.md) | Stock insuficiente tras el cobro: el cliente elige en 48 h y las roturas parciales se anulan por línea | aceptado (negocio: Oscar Torrens) | escenarios del change `handle-stock-shortage-after-payment` |

Mapa de contextos y glosario: `docs/domain/`.
