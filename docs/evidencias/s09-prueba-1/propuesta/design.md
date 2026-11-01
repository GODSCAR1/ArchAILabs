# Design

## Context

El endpoint de búsqueda convierte los parámetros HTTP en un criterio de dominio y el adaptador de persistencia compone `Specification` tipadas para los filtros. El SKU ya existe como value object y como atributo persistido, por lo que el cambio puede reutilizar el flujo actual sin modificar el esquema. La especificación existente `product-search` se modifica, no se crea una capacidad paralela.

## Goals / Non-Goals

**Goals:**

- Transportar el SKU opcional desde HTTP hasta la consulta de búsqueda.
- Normalizar y validar el parámetro con las mismas reglas del value object `Sku`.
- Expresar la comparación como igualdad tipada sobre el valor persistido normalizado.
- Verificar coincidencia, normalización, rechazo de formato inválido y combinación con otros filtros.

**Non-Goals:**

- Añadir índices, migraciones o nuevas dependencias.
- Cambiar la forma de la respuesta o el orden de resultados.

## Decisions

- **Añadir `sku` al criterio de búsqueda:** el filtro forma parte del contrato de búsqueda y debe atravesar la frontera web como dato explícito, en vez de reutilizar `text`, que tiene semántica parcial.
- **Normalizar mediante `Sku`:** el adaptador de entrada construirá el objeto de valor para aplicar `trim`, mayúsculas y formato permitido antes de crear el criterio; un formato inválido se traducirá al error de parámetro de búsqueda.
- **Usar igualdad en la Specification:** la consulta comparará el valor persistido con el SKU normalizado mediante igualdad, evitando `LIKE` y garantizando coincidencia exacta sobre el valor canónico.
- **Mantener el filtro opcional:** si no se recibe `sku`, no se añade ninguna restricción, preservando el comportamiento actual.
- **Pruebas en las fronteras existentes:** las pruebas unitarias del mapper usarán nombres `should_…_when_…`; los escenarios se trazarán en pruebas HTTP e integración, incluida una combinación que compruebe IDs.
- **Preservar guardianas de consulta:** `ProductSearchQueryCountTest` y `ProductSearchPaginationTest` no se modificarán y deberán seguir en verde.

### Reglas de arquitectura

- `domain` no dependerá de Spring, JPA ni Jackson.
- `application` dependerá solo de `domain` y no conocerá infraestructura.
- La conversión HTTP ↔ criterio permanecerá en el mapper web.
- La Specification y el acceso JPA permanecerán en `infrastructure/adapters/out/persistence`.

## Risks / Trade-offs

- [Riesgo] Un formato inválido puede llegar hasta la consulta si se omite la construcción del value object → Se valida y normaliza el parámetro antes de construir el criterio, con prueba HTTP del `ProblemDetail` esperado.
- [Riesgo] El filtro puede devolver cero resultados legítimamente → La respuesta seguirá siendo una página HTTP 200 vacía, igual que los demás filtros.

### Reglas de arquitectura

- `domain` no dependerá de Spring, JPA ni Jackson.
- `application` dependerá solo de `domain` y no conocerá infraestructura.
- La conversión HTTP ↔ criterio permanecerá en el mapper web.
- La Specification y el acceso JPA permanecerán en `infrastructure/adapters/out/persistence`.
