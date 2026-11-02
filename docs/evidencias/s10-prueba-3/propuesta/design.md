# Design

## Context

El catálogo ya expone búsquedas paginadas mediante adaptadores web y MongoDB. El modelo de producto contiene una categoría embebida con id y nombre; el resumen solicitado requiere agrupar productos sin traerlos a la aplicación.

## Goals / Non-Goals

**Goals:** Mantener el conteo en el módulo catalog y seguir la arquitectura hexagonal vigente.

**Non-Goals:** Crear almacenamiento o un CRUD independiente para categorías.

## Decisions

- Añadir un caso de uso y puerto de salida específicos para el resumen por categoría, con un resultado de dominio mínimo que contenga id, nombre y cantidad. Esto mantiene la aplicación independiente de MongoDB.
- Implementar la agrupación mediante una agregación MongoDB sobre la clase tipada de entrada del producto, devolviendo solo los campos agregados. Así el coste de transferencia no crece con el número de productos.
- Publicar la lectura como `GET /api/products/category-counts`, junto a las rutas existentes del catálogo. La respuesta será una lista; MongoDB no devolverá filas para categorías sin productos.
- Ensamblar el caso de uso y el adaptador en la configuración de infraestructura existente; conversión de resultado web en mapper dedicado.

### Reglas de arquitectura

- `application` depende solo de modelos de dominio y puertos de salida de `domain`.
- El adaptador web depende del puerto de entrada de `application`, nunca del adaptador Mongo.
- El adaptador Mongo implementa exclusivamente el puerto de salida de `domain`; la configuración de infraestructura ensambla los beans.

## Risks / Trade-offs

- El recuento refleja el estado observado durante la agregación, no una instantánea transaccional coordinada con escrituras concurrentes → aceptable para un resumen de lectura del catálogo.
- Se cuentan productos de todos los estados; esto queda explícito en el contrato para evitar que el conteo cambie implícitamente según disponibilidad o publicación.
