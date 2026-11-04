# Proposal

## Why

La ficha de producto concentra gran parte de las lecturas y presiona MongoDB durante los picos. Una cache compartida con nivel local reduce ese coste y hace visible la edición rápidamente, manteniendo la obsolescencia máxima aceptada de 30 segundos.

## What Changes

- Añadir cache de dos niveles para `GET /api/products/{sku}`, sin alterar rutas, respuestas ni errores HTTP.
- Cachear únicamente el valor inmutable de la ficha; los resultados de reseñas paginadas y SKU inexistentes no se almacenan.
- Invalidar la ficha tras editar un producto y cuando la proyección detecte cambios de stock, propagando invalidaciones entre instancias.
- Mantener el almacén compartido no disponible como un miss recuperable, medir los accesos de cada nivel y prevenir cargas duplicadas concurrentes por instancia.
- Registrar y programar el job de proyección de stock, que hasta ahora no corría en la aplicación, para refrescar periódicamente el stock de la ficha.
- Añadir reglas de arquitectura y pruebas de integración multi-instancia, Redis y MongoDB.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `product-detail`: definir la cache, su consistencia, fallos y límites; la ruta y el contrato HTTP se mantienen.
- `product-editing`: incluir invalidación de ficha tras commit por edición y cambios de stock detectados por la proyección.

## Impact

- Afecta a la infraestructura de `catalog`, a la publicación de cambios desde aplicación, al listener de invalidación, a la proyección periódica y a las reglas/test de arquitectura.
- Añade configuración de cache y conectividad Redis en infraestructura; no requiere cambios de dependencias ni propiedades fuera del alcance del laboratorio.
- La cache local dura como máximo 16,5 segundos; la compartida dura 10 minutos con jitter. La invalidación normal entre instancias debe propagarse en menos de un segundo y toda instancia debe converger en menos de 30 segundos.
- **Non-goals:** cachear reseñas, búsquedas u otras lecturas; implementar la cache reservada de categorías; prueba de carga del paso 7; cambios en `pom.xml`, propiedades de aplicación, AGENTS, verificación, scripts o zona protegida.
