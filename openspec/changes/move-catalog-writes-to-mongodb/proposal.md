# Proposal

## Why

MongoDB ya puede recibir la proyección del catálogo, pero el selector actual separa las lecturas de las escrituras y deja una configuración ambigua para el corte. Un único `catalog.store` hará explícita la fuente de verdad: PostgreSQL antes del corte y MongoDB después, manteniendo inventory como dueño del stock.

## What Changes

- Sustituir `catalog.read-store` por `catalog.store=postgres|mongo`, que selecciona juntos los adaptadores de lectura y escritura del catálogo.
- Mantener PostgreSQL como modo predeterminado durante la fase de preparación: en ese modo las operaciones de catálogo siguen en PostgreSQL y la migración puede alimentar Mongo; en modo Mongo, MongoDB pasa a ser fuente de verdad de catálogo.
- Activar infraestructura Mongo si `catalog.store=mongo` o `catalog.migration.enabled=true`.
- Copiar `stock.available` desde PostgreSQL periódicamente mediante un proceso por lotes: recorrer productos Mongo por keyset de `_id`, consultar stock para los ids del lote y actualizar en bulk; productos sin fila quedan con cero. El desfase queda documentado.
- Cambiar la suite dual, los tests de selección de adaptadores y añadir equivalentes Mongo de los guardianes en clases nuevas.
- Organizar la transición en tres fases: trabajo del agente con PostgreSQL por defecto; ajustes de guardianes a cargo de una persona; y después cambio del default a Mongo.
- El corte se realiza como procedimiento operativo: mantener `store=postgres` y `migration.enabled=true` hasta el checkpoint `COMPLETADO`, después usar `store=mongo` y `migration.enabled=false`. No se añade un barrido final automático.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `product-editing`: las escrituras y lecturas se seleccionan juntas con `catalog.store`; PostgreSQL es el modo inicial y Mongo el modo final de fuente de verdad del catálogo.

## Impact

- Afecta los puertos de lectura/escritura del catálogo, configuración, migración, job de stock, suite dual, guardianes equivalentes y documentación de corte.
- Antes de cambiar el default, los tests guardianes `@SpringBootTest` basados en PostgreSQL necesitan configuración explícita `catalog.store=postgres` o ser retirados por una persona.
- Si se vuelve a PostgreSQL después de escrituras exclusivas en Mongo, esas escrituras no estarán visibles hasta reconciliarlas.

## Non-goals

- Borrar tablas, adaptadores JPA ni el selector; eso pertenece a `retire-catalog-postgres`.
- Introducir outbox, eventos o doble escritura.
- Añadir APIs de escritura para variantes, atributos o reseñas.
- Cambiar inventory u ordering, ni trasladar a Mongo la autoridad de stock.
- Modificar los tests protegidos o `scripts/protected-tests.txt` desde el agente; esa intervención pertenece a la persona en la Fase B.
