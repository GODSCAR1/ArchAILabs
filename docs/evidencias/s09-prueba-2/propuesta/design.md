# Design

## Context

El mapper web valida los campos de `sort` antes de crear el criterio de búsqueda. El adaptador de persistencia ya añade `id` ascendente como desempate. El stock es una asociación de lectura opcional del producto, por lo que la ordenación debe convertir la ausencia de fila de stock en cero de forma explícita.

## Goals / Non-Goals

**Goals:**

- Traducir `stock` a una expresión de persistencia que representa las unidades disponibles y convierte un valor ausente en cero.
- Mantener la lista blanca en el límite HTTP y el desempate final por `id`.
- Cubrir validación HTTP y ordenación real con Testcontainers, incluyendo valores repetidos y productos sin stock.

**Non-Goals:**

- Cambiar el modelo o la API pública de inventory.
- Añadir una columna derivada o una migración.

## Decisions

- **Construir la ordenación con `CriteriaBuilder.coalesce(stock.available, 0)`.** Usa la fuente de datos existente, evita duplicar el valor disponible y define explícitamente el tratamiento de productos sin stock. Se descarta un `Sort` sobre la ruta `stock.available`, porque PostgreSQL deja los `NULL` al final en orden ascendente.
- **Conservar `id` como segundo criterio.** Hace estable la paginación cuando varias filas comparten stock. Se descarta confiar en el orden natural de la base de datos.
- **Mantener `stock` como nombre HTTP y mapearlo internamente.** El cliente no queda acoplado al nombre de la propiedad JPA. La lista blanca sigue siendo explícita.

## Reglas de arquitectura

- El adaptador web solo traduce el parámetro HTTP a `ProductSearchOrder`; no accede a persistencia.
- La aplicación continúa dependiendo únicamente de puertos y modelos de `domain`; no se introducen dependencias hacia `infrastructure`.
- La implementación de ordenación permanece en `infrastructure/adapters/out/persistence`; `domain` y `application` no conocen JPA ni Spring Data.
- No se crean dependencias entre `catalog`, `inventory` y `ordering`; el catálogo usa únicamente su puerto de búsqueda.

## Risks / Trade-offs

- [Productos sin fila de stock] → Verificar que `coalesce` los ordena como cero en la prueba de integración; no cambiar el fetch a `EAGER`.
- [Cambio accidental de paginación] → Reutilizar el criterio final por `id` y comprobar que cada producto aparece una sola vez con stock repetido.
- [Regresión de guardianes] → Mantener `ProductSearchPaginationTest` y `ProductSearchQueryCountTest` sin cambios y comprobar que siguen en verde.

## Migration Plan

No hay migración de datos ni despliegue especial. Tras desplegar el cambio, los clientes pueden usar `sort=stock,asc` o `sort=stock,desc`; el rollback consiste en retirar la aceptación de ese valor y el mapeo asociado.
