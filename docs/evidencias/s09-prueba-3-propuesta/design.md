# Design

## Context

La reserva directa adquiere un bloqueo pesimista con timeout y actualmente traduce un timeout de bloqueo a `StockBusyException`, que el adaptador HTTP expone como `503`. La transacción que contiene el intento no puede reutilizarse después de un fallo de bloqueo, por lo que los reintentos deben abrir transacciones nuevas.

## Goals / Non-Goals

**Goals:**

- Repetir solo los fallos identificados como contención recuperable del stock.
- Mantener cada lectura bloqueada y escritura dentro de una transacción completa e independiente.
- Permitir que los resultados de negocio (`201`, `409` y `404`) terminen la petición normalmente.
- Garantizar que la ruta HTTP directa no convierta una contención recuperable en `503`.

**Non-Goals:**

- Cambiar el timeout del bloqueo de la base de datos.
- Reintentar errores de validación, stock insuficiente, producto inexistente o fallos de infraestructura no relacionados con el bloqueo.
- Añadir una cola, persistencia de intentos o procesamiento asíncrono.

## Decisions

### Reintento en el ensamblado de infraestructura

El bean que expone `ReserveProductStockUseCase` ejecutará un bucle que invoca `TransactionTemplate.execute(...)` una vez por intento y repetirá únicamente cuando el delegado lance `StockBusyException`. Así el caso de uso y el dominio permanecen independientes de Spring, y cada intento tiene un contexto transaccional nuevo.

La alternativa descartada es aumentar el timeout o capturar el error en el controller: aumentar el timeout sigue produciendo fallos bajo contención suficiente, y el controller no puede reabrir correctamente la transacción del caso de uso.

### Contrato HTTP

La respuesta de contención deja de ser una salida normal del endpoint. El handler específico de `StockBusyException` se eliminará o dejará de estar asociado a la reserva directa, porque esa excepción solo debe escapar ante una condición no contemplada por el contrato de reintento. Los errores de negocio existentes conservan sus tipos y códigos.

### Espera entre intentos

Los intentos se realizarán de nuevo tras cada timeout del bloqueo, sin límite de cantidad ni deadline de aplicación. No se añade una espera artificial: el timeout de base de datos ya proporciona el intervalo de backoff y evita un bucle ocupado.

### Reglas de arquitectura

- `domain` no depende de Spring, JPA ni Jackson; `StockBusyException` continúa siendo una excepción de dominio.
- `application` solo depende de `domain`; no conocerá la política de reintento ni `TransactionTemplate`.
- `infrastructure/config` ensamblará `TransactionTemplate` y la política de reintento mediante `@Bean`, sin lógica de negocio.
- El adaptador de persistencia seguirá siendo el único traductor del error de bloqueo de JPA/Spring a `StockBusyException`.
- `ordering` solo consumirá la fachada pública de `inventory` y no dependerá de sus adaptadores ni de la política de reintento.

## Risks / Trade-offs

- [Petición bloqueada indefinidamente] → Es intencionado por el contrato solicitado; el timeout de cada bloqueo sigue liberando la conexión y permite reintentar.
- [Acumulación de hilos bajo contención prolongada] → Se mantiene el alcance síncrono actual y se verifica que no haya respuestas `503` durante concurrencia normal; una cola asíncrona queda fuera de alcance.
- [Fallo permanente de la base de datos] → No se capturan ni reintentan excepciones que no sean `StockBusyException`, para no ocultar fallos no recuperables.

## Migration Plan

No hay migración de datos ni cambio de configuración. Desplegar el cambio de código y validar con las pruebas de transacción y concurrencia. Para revertirlo, restaurar la política anterior que expone `StockBusyException` como `503`; no requiere transformación de datos.
