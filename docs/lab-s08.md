# Lab S08 — Perfilar y optimizar un endpoint N+1

## Paso 1: medición inicial (línea base)
Comando: `bash ./mvnw -q verify -Dit.test=OrderSummaryProfilingIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false`

| Pedidos | Sentencias | Entidades | Colecciones | Tiempo |
|---|---|---|---|---|
| 7 | 15 (1 + 7 + 7) | 14 | 7 | 15 ms |
| 500 | 1.001 (1 + 500 + 500) | 1.000 | 500 | 1.009 ms |

Sentencias repetidas (incluye calentamiento):
- 1014 × `select ... from order_lines where order_id=?` (EAGER: una por pedido, sin tocar `lines`)
- 1014 × `select ... from customers where id=?` (`findById` en el bucle del adaptador)
- 4 × `select ... from orders where status=?`

## Paso 2: test que falla
`OrderSummaryQueryAdapterQueryCountTest` (120 pedidos): **241 > 2** → rojo. Las 120 de líneas salen juntas tras `orders` (EAGER al consultar); las 120 de clientes, después (bucle del `map`).

## Hallazgos
1. `@ElementCollection(fetch = EAGER)` en `OrderJpaEntity` desde el lab S05 (commit fd0daa2), sin justificación en design/spec: nadie lo detectó porque no había regla en Guide ni check en Verify.
2. `--full`: "Surefire is going to kill self fork JVM" (10 contextos/contenedores → apagado > 30 s). Aviso, exit=0.
3. `@DataJpaTest` activa `show-sql` por defecto → 241 líneas en stdout que tapan el mensaje del assert en el gate.
4. Mensajes del assert con mojibake en la consola de Windows (UTF-8 vs cp850).
5. Al escribir el test en rojo, el gate `session.idle` lo ejecutó (fast loop), falló y el agente "corrigió" `src/main`
   (join fetch de lines + findAllById) pese a la instrucción "no toques src/main": el gate pesa más que el prompt.
   Su arreglo da 2 consultas; guardado en `docs/alt-agente.patch`.
6. A agrupa las líneas (`order_id = any (?)` en PostgreSQL) pero NO los clientes: `findById` en un bucle es una consulta
   explícita, no una carga perezosa de Hibernate. Consecuencia de no mapear `@ManyToOne` (sin FK).
7. B: 2 consultas constantes; el join devuelve 1.000 filas para 500 pedidos (2 líneas c/u). `findAllById` genera un `IN`
   con un parámetro por id: comprobar el límite de 1.000 elementos de Oracle antes de usarlo en RSI.
8. C: Hibernate traduce `count(l)` de un embeddable a `count((l.product_id, l.quantity))`. Con `left join`, un pedido sin
   líneas produce la fila `(NULL, NULL)`, que para `count` NO es nula → **confirmado: `lineCount = 1` en vez de 0**
   (test `OrderSummaryQueryAdapterIT.should_count_zero_lines_when_order_has_no_lines` en rojo). Los tests de la spec y el
   de consultas pasaban: ninguno cubría el caso. Corregido con `count(l.productId)` (cuenta solo valores no nulos).

## Paso 3: alternativas (ramas `lab/s08-alt-a|b|c`, cada una desde `lab/s08-baseline`)
Medición: `OrderSummaryQueryAdapterQueryCountTest` (120 pedidos) + `OrderSummaryProfilingIT` (7 y 500).

| Alternativa | Qué cambia | 7 | 120 (test) | 500 | Tiempo (500) | Entidades (500) | Filas leídas (500) | ¿Una colección por consulta? | ¿Pasa ≤ 2? |
|---|---|---|---|---|---|---|---|---|---|
| Línea base | — | 15 | 241 | 1.001 | 1.009 ms | 1.000 | 1.500 | — | ❌ |
| A. Batch global | `default_batch_fetch_size=50` | 9 | 124 | 511 | 499 ms | 1.000 | 1.500 | — | ❌ |
| B. Entity Graph | `@EntityGraph(attributePaths = "lines")` + `findAllById` | 2 | 2 | 2 | 36 ms | 1.000 | 1.000 + 500 | ✅ (solo `lines`) | ✅ |
| C. Proyección | JPQL `select new OrderSummaryRow(...)` con `left join` + `group by` | 1 | 1 | 1 | 7 ms | 0 | 500 (agregadas en la BD) | ✅ (sin colecciones) | ✅ |

- La solución espontánea del agente (`docs/alt-agente.patch`: `left join fetch` en JPQL + `findAllById`) equivale a B: 2 consultas.
- B y C: test de consultas en verde. C: `verify-quality.sh --full` → exit=0 (incluido el cliente huérfano con `left join`).
9. Tras cambiar de rama, `target/classes` conservaba `V5__create_warehouses.sql` y `WarehouseJpaEntity.class` de la
   prueba 2: Flyway aplicó una migración que no existe en la rama (entorno no honesto). `./mvnw clean` al cambiar de rama.
