# Design

## Context

See proposal.md - Why. El catálogo Mongo actual almacena una lista `latestReviews` limitada a cinco y las reseñas completas en una colección aparte; las reseñas se consultan con paginación. MongoDB limita cada documento a 16 MB.

## Goals / Non-Goals

**Goals:**
- Resolver la ficha y sus reseñas desde un único documento del producto.
- Preservar todas las reseñas existentes dentro del producto y rechazar atómicamente las altas que superen el límite documental.

**Non-Goals:**
- Eliminar el límite físico de 16 MB ni limitarlo a un valor inferior configurable.
- Cambiar la valoración agregada o la consistencia del stock.

## Decisions

- Mantener las reseñas completas en un array del documento de producto y ordenar/paginar dicho array para las consultas; no mantener una copia en otra colección, que conservaría la operación adicional y generaría dos fuentes de lectura.
- Antes de guardar una reseña, comprobar si el documento resultante cabe en el máximo BSON admitido; rechazar la escritura completa con el error especificado en la spec cuando no quepa. Una actualización condicional debe impedir que escrituras concurrentes pierdan reseñas.
- Migrar las reseñas existentes agrupándolas por producto y escribir el agregado mediante lotes idempotentes; verificar recuentos antes de retirar la colección antigua. Productos cuyo agregado existente ya exceda el máximo bloquean la migración y requieren resolución operativa antes del corte.
- Conservar la lectura de ficha como una operación, y adaptar la consulta paginada para leer y paginar reseñas del agregado, midiendo el número de operaciones y el tamaño de los documentos.

## Risks / Trade-offs

- [Los productos muy reseñados alcanzarán el límite y no admitirán nuevas reseñas] → Rechazar de forma explícita y medible la reseña que exceda el máximo; no descartar reseñas existentes.
- [Un documento grande aumenta bytes leídos y escritos aunque haya una sola operación] → Medir tamaño BSON, latencia y volumen con conjuntos de reseñas representativos; una operación no garantiza menor tiempo en todas las cargas.
- [La migración puede encontrar productos que ya superan el máximo] → Validar tamaño y recuentos antes de cortar; no eliminar la colección origen hasta completar la verificación.
- [Escrituras concurrentes pueden sobrescribir actualizaciones del array] → Usar comparación de versión en la actualización atómica.

## Migration Plan

1. Inventariar tamaño BSON y número de reseñas por producto; detener el corte si algún agregado supera el máximo.
2. Preparar y ejecutar una migración idempotente que integre las reseñas y permita comparar recuentos.
3. Desplegar lecturas y escrituras desde el agregado, comprobar conteos de reseñas, operación única de ficha y errores de límite.
4. Retirar la colección antigua solo tras verificar migración completa.
5. Para rollback, detener altas, reconstituir la colección de reseñas desde los arrays y verificar recuentos antes de volver a la versión anterior.

## Reglas de arquitectura

- El dominio y aplicación del catálogo permanecen independientes de MongoDB y de los adaptadores de infraestructura.
- Los adaptadores web dependen de puertos de aplicación; los adaptadores Mongo implementan puertos de salida de dominio.
- La configuración de infraestructura ensambla los puertos; no se añade dependencia entre catálogo, inventory y ordering.

## Open Questions

- Ninguna para el alcance de esta propuesta.
