# ADR: listado de pedidos por estado (lab S08)

- **Estado:** aceptado
- **Fecha:** 2026-10-24
- **Contexto de origen:** change `add-paid-orders-listing`; medición y alternativas en `docs/lab-s08.md`.

## Contexto
`GET /api/orders?status=PAID` devuelve por pedido `id`, `customerName`, `lineCount` y `totalUnits`. La primera versión
(línea base) mapeaba entidades en `OrderSummaryQueryAdapter`: 1 consulta de pedidos + 1 por pedido para las líneas
(`@ElementCollection(fetch = EAGER)`) + 1 por pedido para el cliente (`findById` en el bucle).

| Pedidos | Consultas | Tiempo |
|---|---|---|
| 7 | 15 | 15 ms |
| 500 | 1.001 | 1.009 ms |

Restricciones:
- `orders.customer_id` **no tiene FK** hacia `customers`: hay pedidos históricos cuyo cliente no existe y deben listarse con
  `customerName = null`.
- Por eso no hay `@ManyToOne` de `Order` a `Customer`: un proxy LAZY sobre una clave colgante lanza
  `EntityNotFoundException` al inicializarse, y un join fetch puede fallar. El vínculo es solo por id.

## Alternativas medidas
| Alternativa | 7 | 120 | 500 | Tiempo (500) | Entidades (500) | Filas leídas (500) | Umbral ≤ 2 |
|---|---|---|---|---|---|---|---|
| Línea base | 15 | 241 | 1.001 | 1.009 ms | 1.000 | 1.500 | ❌ |
| A. `default_batch_fetch_size=50` | 9 | 124 | 511 | 499 ms | 1.000 | 1.500 | ❌ |
| B. `@EntityGraph(lines)` + `findAllById` | 2 | 2 | 2 | 36 ms | 1.000 | 1.000 + 500 | ✅ |
| C. Proyección JPQL (`select new`, `left join`, `group by`) | 1 | 1 | 1 | 7 ms | 0 | 500 | ✅ |

## Decisión
1. **Listado → C (proyección).** Una consulta constante, sin entidades gestionadas, agregación en la base de datos y
   `left join` a `customers`, que resuelve el cliente huérfano sin proxies.
2. **Detalle de un pedido (futuro) → B (Entity Graph).** Cuando el caso de uso necesite la entidad completa (para
   modificarla o devolver sus líneas), `@EntityGraph` con **una sola colección**; los clientes por `findAllById`.
3. **Red de seguridad → A siempre activa:** `spring.jpa.properties.hibernate.default_batch_fetch_size=50` en
   `application.properties` (zona protegida). No es la solución de ningún listado: convierte un N+1 que se escape en N/50 + 1.

## Consecuencias
- **Positivas:** el listado no depende del número de pedidos (1 consulta con 7, 120 o 500) y baja de 1.009 ms a 7 ms
  con 500 pedidos. El test `OrderSummaryQueryAdapterQueryCountTest` (≤ 2 con 120 pedidos) lo protege en el fast loop.
- **`count` en la proyección:** hay que contar una columna no nula de la línea (`count(l.productId)`), nunca el embeddable
  (`count(l)`): Hibernate lo traduce a `count((product_id, quantity))` y un pedido sin líneas cuenta 1 por el `left join`.
  Cubierto por `OrderSummaryQueryAdapterIT.should_count_zero_lines_when_order_has_no_lines`.
- **La proyección no devuelve entidades:** no sirve para casos de uso que modifiquen el pedido (para eso, B).
- **A no agrupa consultas explícitas:** `findById` dentro de un bucle no se beneficia del batch fetching (solo las cargas
  perezosas que gestiona Hibernate). La red no sustituye a corregir el bucle.
- **Con A activa, el test de consultas necesita más filas que el tamaño del lote** (120 > 50): con 7 filas, un N+1
  parcial daría 2 consultas y pasaría en verde. Si cambia el lote, cambia el número de filas del test.
- **B en Oracle (RSI):** `findAllById` genera un `IN` con un parámetro por id; comprobar el límite de 1.000 elementos
  antes de reutilizar el patrón.
- **Pendiente (protección del loop):** pasar `lines` a LAZY (el EAGER venía del lab S05 sin justificación) y prohibir
  `FetchType.EAGER` con Checkstyle; `application*` en la zona protegida de Claude Code y OpenCode.

## Alternativas descartadas
- **A como solución:** 124 consultas con 120 pedidos; sigue dependiendo del número de filas.
- **B para el listado:** correcta (2 consultas), pero carga 1.000 entidades y multiplica filas por el join; innecesario
  para una respuesta de solo lectura con cuatro campos.
- **`@ManyToOne` hacia `Customer`:** frágil sin FK (ver Contexto).
- **Añadir la FK:** rompe los pedidos históricos sin cliente.

## Protección (lab S08, paso 5)
- Guide: sección "Persistencia (JPA)" en `AGENTS.md`.
- Verify: Checkstyle `toOneLazy` y `noEagerFetch`; `OrderSummaryQueryAdapterQueryCountTest` (≤ 2 con 120 pedidos) en el
  fast loop; segunda capa en `verify-quality.sh` que exige `open-in-view=false`, `default_batch_fetch_size=50` y prohíbe
  `enable_lazy_load_no_trans`.
- Zona protegida: `src/main/resources/application*` en `protect-verify.sh` y en el plugin de OpenCode. No basta sola:
  el agente la sorteó por el shell (prueba 4); la segunda capa en Verify es la que no depende del guard.
