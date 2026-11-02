# Design

## Context

El buscador recibe parámetros HTTP y los transforma en un criterio de dominio; el adaptador Mongo aplica cada filtro a ambas consultas del listado (página y total). Los productos guardan sus atributos en un mapa y la inicialización de índices del catálogo se centraliza en su inicializador. Ver `proposal.md` para la motivación y `specs/product-search/spec.md` para el contrato observable.

## Goals / Non-Goals

**Goals:**

- Mantener `brand` como filtro opcional y combinarlo en la misma consulta de página y conteo.
- Aplicar igualdad completa sin distinguir mayúsculas sobre `attributes.brand`.
- Seguir la convención del catálogo de declarar índices y comprobarlos en la prueba estructural existente.

**Non-Goals:**

- Crear consultas adicionales para buscar marcas o modificar los documentos de producto.
- Añadir reglas de validación de marca no solicitadas ni variar el número de operaciones del buscador.

## Decisions

### Representación y semántica del filtro

El valor HTTP opcional `brand` se incorporará al criterio existente y se resolverá contra la clave `brand` del mapa de atributos. Se usará una expresión anclada y escapada como literal para lograr igualdad completa y comparación sin distinguir mayúsculas, evitando que caracteres de regex enviados por el cliente cambien el significado del filtro. Los productos sin ese atributo no coincidirán cuando se solicite una marca.

Se elige reutilizar el mapa ya persistido en vez de añadir un campo de marca independiente: así el filtro no requiere cambiar creación, edición ni validación de documentos. La normalización de valores durante escritura queda fuera de alcance.

### Consultas e índice

El filtro se añade al conjunto común de criterios usado tanto por el `count` como por la consulta paginada, sin lanzar una operación adicional. Se declara un índice ascendente sobre `attributes.brand` en `CatalogIndexInitializer` y se verifica junto con los índices existentes en `CatalogMongoStructureIT`.

Se conserva el esquema de consulta establecido por el adaptador en lugar de introducir una agregación o una búsqueda separada. El patrón anclado preserva la igualdad exacta; por llevar opción de ignorar mayúsculas, MongoDB podría no aprovechar plenamente un índice simple en todos los planes de ejecución.

### Reglas de arquitectura

- El modelo y el criterio del dominio no dependen de MongoDB ni de infraestructura.
- El adaptador de infraestructura depende del dominio y es el único lugar que construye criterios propios de MongoDB.
- No se añaden dependencias entre módulos ni se cambia el ensamblado de beans.

## Risks / Trade-offs

- [Riesgo] La comparación sin distinguir mayúsculas con regex puede limitar el uso del índice ascendente simple. → Se conserva el índice obligatorio del campo filtrado y se mantiene constante el número de operaciones; no se promete una mejora de latencia.
- [Riesgo] Productos sin `attributes.brand` no aparecen al filtrar por marca. → Este comportamiento es parte de la semántica de igualdad y no requiere migración de documentos.

## Migration Plan

No se requiere migración de datos: el mapa `attributes` ya forma parte del documento y el índice se asegura al inicializar la estructura del catálogo. El cambio es reversible retirando el parámetro y el índice; los datos de producto no se modifican.
