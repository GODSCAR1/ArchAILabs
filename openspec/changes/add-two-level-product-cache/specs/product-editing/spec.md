# Spec Delta

## ADDED Requirements

### Requirement: La edición invalida la ficha después del commit
El sistema SHALL invalidar la ficha correspondiente después de confirmar una edición válida, propagando la invalidación a las instancias activas sin modificar el contrato HTTP de edición.

#### Scenario: Edición válida invalida la ficha
- **GIVEN** `SKU-CAFE` está cacheado con precio `10000` y su versión actual es `4`
- **WHEN** un cliente edita el producto con versión `4` y precio `12000`
- **THEN** el PUT responde HTTP 200 con el contrato existente y la siguiente lectura de ficha responde HTTP 200 con precio `12000`

#### Scenario: Edición rechazada conserva la ficha cacheada
- **GIVEN** `SKU-CAFE` está cacheado con precio `10000` y su versión actual es `5`
- **WHEN** un cliente envía un PUT con versión obsoleta `4` y precio `12000`
- **THEN** el PUT responde HTTP 409 con ProblemDetail type `https://api.master-acdc.dev/problems/concurrent-modification`, la ficha sigue respondiendo HTTP 200 con precio `10000` y no se publica ninguna invalidación

#### Scenario: Fallo del almacén compartido al invalidar no bloquea las copias locales
- **GIVEN** las instancias A y B tienen cacheado `SKU-CAFE` con precio `10000` y el almacén compartido falla durante la invalidación
- **WHEN** se edita el producto a precio `12000` en A
- **THEN** el PUT responde HTTP 200 y A y B responden la ficha con precio `12000`; las copias locales se invalidan aunque falle el almacén compartido

### Requirement: La proyección de stock invalida solo las fichas modificadas
El sistema SHALL invalidar las fichas de los SKU cuyo stock disponible cambió en una pasada de sincronización y SHALL conservar cacheadas las fichas cuyo stock no cambió.

#### Scenario: Cambio de stock en la proyección
- **GIVEN** `SKU-CAMBIA` está cacheado con stock `3` y `SKU-ESTABLE` con stock `6`
- **WHEN** una pasada cambia el stock de `SKU-CAMBIA` a `4` y mantiene `SKU-ESTABLE` en `6`
- **THEN** la ficha de `SKU-CAMBIA` responde HTTP 200 con stock `4` y la ficha cacheada de `SKU-ESTABLE` no se invalida

#### Scenario: El job ejecuta la sincronización periódica
- **GIVEN** la sincronización periódica está habilitada, `SKU-CAFE` tiene precio `10000`, stock proyectado `2` y el origen informa stock `5`
- **WHEN** la aplicación ejecuta el siguiente ciclo programado
- **THEN** `GET /api/products/SKU-CAFE` responde HTTP 200 con precio `10000` y stock `5`
