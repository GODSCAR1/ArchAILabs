# ADR 0016 — Efectos síncronos de liberación de stock y alta de cliente

- Estado: aceptado (lab S20, 2026-11-26)
- Evidencias: `docs/lab-s20.md` (diferencias entre la vista de componentes y el mapa), `docs/architecture/workspace.dsl`
- Relacionados: ADR 0014 (amplía su punto 4), ADR 0015 (reserva síncrona)

## Contexto

- Al derivar la vista de componentes del código (lab S20) aparecieron dos efectos síncronos entre
  contextos que el punto 4 del ADR 0014 no cataloga.
- `OrderCancelledListener` (en `ordering`) llama a `InventoryFacade.release` en la misma transacción
  que la cancelación: Pedidos ordena a Inventario liberar stock.
- `CustomerCreatorAdapter` (en `identity`) llama a `CustomerRegistrationFacade.register` dentro de la
  transacción del registro: Identidad ordena a Pedidos crear el cliente.
- `ContextMapTest` no los detectó porque cataloga tipos, no métodos: `InventoryFacade` y
  `CustomerRegistrationFacade` ya estaban en la lista.

## Decisión

1. Se añaden dos excepciones al punto 4 del ADR 0014 (siguen siendo una lista cerrada):
   - `InventoryFacade.release` desde `ordering`: es la compensación de `reserve` y comparte su razón de
     negocio (ADR 0015). Una cancelación que no libera stock se revierte entera.
   - `CustomerRegistrationFacade.register` desde `identity`: una cuenta de usuario sin cliente no puede
     comprar, así que las dos altas fallan juntas.
2. Las dos son deuda: pasan a evento en la Sección 39, junto con las tres excepciones del ADR 0014.
3. Las dos relaciones llevan el tag `Deuda` en `docs/architecture/workspace.dsl`.

**Verificación:** `ContextMapTest.cross_context_calls_only_use_catalogued_ports` (los dos puertos están en
la lista cerrada) y `validate` de Structurizr sobre `workspace.dsl` en `verify-quality.sh --full`.

## Consecuencias

- ✅ El ADR 0014, el mapa, el diagrama y el código vuelven a decir lo mismo.
- ✅ La Sección 39 parte de cinco excepciones conocidas, todas visibles en rojo en la vista de componentes.
- ❌ La cancelación de un pedido depende de que Inventario responda: si falla, el pedido no se cancela.
- ❌ El registro de una cuenta depende de Pedidos: un fallo allí impide registrarse.
- ❌ El test cataloga tipos, no métodos: un método nuevo con efecto en esas fachadas no rompería el build.

## Alternativas

- Mover los listeners a Inventario y a Pedidos ya: descartada. Es el trabajo de la Sección 39 y cambia comportamiento.
- Dejarlo solo como deuda en el mapa, sin ADR: descartada. El ADR 0014 dice "solo estas" y dejaría de ser cierto.
