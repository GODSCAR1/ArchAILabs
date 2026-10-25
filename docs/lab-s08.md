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
