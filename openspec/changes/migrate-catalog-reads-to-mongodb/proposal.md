# Proposal

## Why

Las lecturas de la ficha del catálogo requieren hoy varias consultas relacionales para
reconstruir un documento que se consume como una unidad, mientras que las reseñas crecen sin
límite. El modelo documental de MongoDB permite servir la ficha agregada en una consulta y
mantener las reseñas completas paginadas aparte, sin cambiar el contrato HTTP ni trasladar la
fuente de verdad de las escrituras.

## What Changes

- Añadir un almacén documental de lectura para productos, categorías embebidas, variantes,
  atributos, stock disponible, resumen de valoración y hasta cinco reseñas recientes.
- Crear explícitamente la validación del documento y los índices requeridos al arrancar, sin
  creación automática de índices.
- Añadir una re-migración periódica, configurable y desactivable mediante
  `catalog.migration.enabled`, desde PostgreSQL hacia MongoDB, con upsert por SKU, paginación
  keyset por UUID, checkpoint persistente e informe de resultados. Cada ciclo completo vuelve a
  recorrer todos los productos para propagar cambios; un checkpoint incompleto solo reanuda el
  barrido interrumpido.
- Mantener las reseñas completas en una colección separada y recalcular rating y reseñas recientes
  durante cada migración.
- Seleccionar las implementaciones de lectura mediante `catalog.store`, con `postgres` como
  valor por defecto y `mongo` como alternativa. La lectura y la migración se configuran por
  separado, de modo que MongoDB puede alimentarse mientras se sigue leyendo desde PostgreSQL; las
  escrituras continúan en PostgreSQL.
- Ejecutar la misma suite de API para ambos modos y verificar recuentos, equivalencia de fichas e
  idempotencia de la migración.
- No modificar ningún endpoint, payload, código HTTP ni `type` de ProblemDetail existente.

## Capabilities

### New Capabilities

- `catalog-read-store`: almacén documental de lectura del catálogo, re-migración periódica,
  configuración de selección y equivalencia entre PostgreSQL y MongoDB.

### Modified Capabilities

- Ninguna. Los requisitos existentes de `product-search` permanecen sin cambios; este cambio
  sustituye únicamente la implementación seleccionada detrás del puerto de lectura.

## Impact

- Afecta a la infraestructura de persistencia y configuración del módulo `catalog`, además de
  añadir el proceso de migración y su checkpoint.
- Añade MongoDB como dependencia operativa de lectura y como contenedor de integración, sin
  convertirlo en dueño de productos, stock o reseñas de escritura.
- Requiere adaptar las pruebas de integración HTTP para ejecutar PostgreSQL y MongoDB en ambos
  valores de `catalog.store`. Los tests existentes que no habilitan lectura Mongo ni
  migración no deben necesitar MongoDB.
- El modo por defecto y el contrato HTTP siguen siendo compatibles; el modo `mongo` introduce la
  posibilidad observable de activar lecturas desde el nuevo almacén.

## Non-goals

- No migrar las escrituras del catálogo a MongoDB.
- No eliminar tablas PostgreSQL ni los adaptadores JPA de lectura; eso pertenece a otro change.
- No introducir outbox, eventos, doble escritura ni filtros nuevos del buscador.
- No cambiar `AGENTS.md`, la zona protegida ni los contratos HTTP existentes.
