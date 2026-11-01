# Design

## Context

El módulo `ordering` ya persiste pedidos en `orders` y sus líneas en `order_lines`, y expone creación y cancelación desde `OrderController`. `OrderJpaEntity` conserva `customerId` para escritura y carga las líneas con `FetchType.EAGER`. La gestión de errores HTTP usa `OrderExceptionHandler` y `ProblemDetail` con types propios.

La propuesta y el delta de `specs/ordering/spec.md` definen el nuevo contrato. Esta versión debe ser una línea base sencilla: no se introducen consultas optimizadas ni cambios en los casos de uso existentes.

## Goals / Non-Goals

**Goals:**

- Añadir una lectura hexagonal de pedidos filtrada por `OrderStatus`.
- Mantener la ausencia de cliente referenciado como un resultado válido con nombre nulo.
- Asegurar una transacción de lectura explícita y conservar la carga EAGER de líneas.
- Cubrir API, aplicación y persistencia con pruebas unitarias, web e integración real con PostgreSQL.

**Non-Goals:**

- No cambiar la semántica ni la transaccionalidad de crear y cancelar.
- No usar una proyección SQL, `join fetch`, agregación en base de datos ni paginación en esta línea base.
- No hacer que `customers` sea un agregado gestionable por ordering.

## Decisions

### Modelo y puertos

- Añadir `OrderSummary` como `record` inmutable en `ordering.domain.model`, con `UUID id`, `String customerName`, `int lineCount` e `int totalUnits`.
- Añadir `OrderSummaryQuery` como interfaz en `ordering.domain.port`, con una operación para obtener resúmenes por `OrderStatus`.
- Añadir `ListOrdersByStatusUseCase` en `ordering.application.port` y `ListOrdersByStatusService` en `ordering.application`. El servicio delegará en el puerto sin conocer persistencia ni HTTP.

### Persistencia y mapeo

- Crear V4 con `customers(id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL)`, sin `FOREIGN KEY` desde `orders.customer_id`.
- Crear `CustomerJpaEntity` y `CustomerJpaRepository` de solo lectura en `ordering.infrastructure.adapters.out.persistence`. No añadir ninguna asociación JPA desde `OrderJpaEntity` hacia `CustomerJpaEntity`; `customerId` seguirá siendo el único vínculo entre ambos modelos.
- Añadir al repositorio JPA una búsqueda por estado y mapear cada entidad en `OrderSummaryQueryAdapter`: `customerJpaRepository.findById(customerId).map(CustomerJpaEntity::name).orElse(null)` para el nombre, `lines.size()` para `lineCount` y la suma de `quantity` para `totalUnits`.
- Mantener esta resolución deliberadamente sencilla y por entidad: no usar `findAllById`, joins, proyecciones ni agregación SQL. No usar `@NotFound`, no añadir un fetch EAGER, no usar `EntityManager.find` sobre proxies y no capturar `Exception` ni `RuntimeException`.
- No cambiar `@ElementCollection(fetch = FetchType.EAGER)`. La posible multiplicación de consultas por clientes forma parte de la línea base que se medirá.

### HTTP y transacción

- Extender `OrderController` con `GET /api/orders`; exigir `status`, convertirlo a `OrderStatus` y traducir ausencia o valor desconocido a excepciones específicas.
- Extender `OrderExceptionHandler` con los types `missing-order-status` e `invalid-order-status`, ambos HTTP 400.
- Responder con una lista de DTOs HTTP derivados de `OrderSummary`, sin exponer entidades JPA ni añadir el estado al resumen porque no forma parte del contrato solicitado.
- Ensamblar el caso de uso en `OrderingConfiguration` con `TransactionTemplate` configurado como read-only. El bean envolverá el servicio, igual que los flujos transaccionales actuales, pero con la configuración de solo lectura.

### Reglas de arquitectura

- `ordering.domain.model` y `ordering.domain.port` no dependen de Spring, JPA ni Jackson.
- `ordering.application` solo depende de `ordering.domain`; no depende de `ordering.infrastructure`, `inventory` ni adaptadores HTTP.
- `OrderSummaryQuery` permanece como puerto de salida en `ordering.domain.port`; su implementación JPA vive en `ordering.infrastructure.adapters.out.persistence`.
- Los beans se ensamblan en `ordering.infrastructure.config`; ningún servicio o adaptador obtiene beans mediante `ApplicationContext`.
- `ordering` puede usar las APIs públicas de `inventory` únicamente en los flujos ya existentes de reserva/liberación; el nuevo listado no crea una dependencia de `inventory`.

### Alternatives considered

- **Listar todos los pedidos cuando falta `status`:** descartado porque hace implícita una consulta potencialmente amplia y contradice el filtro explícito solicitado.
- **Resolver el nombre con una consulta SQL/proyección agregada:** descartado para preservar la línea base de medición y la decisión explícita de mapear entidades en el adaptador.
- **Añadir una clave foránea a `customers`:** descartado porque existen pedidos históricos cuyo `customer_id` no tiene fila correspondiente.

## Risks / Trade-offs

- [Riesgo] La resolución individual mediante `CustomerJpaRepository.findById` hace una consulta de cliente por cada pedido y puede generar un patrón N+1. → Se conserva deliberadamente como línea base y se mide antes de optimizar.
- [Riesgo] `customerName` puede ser nulo por referencias históricas sin cliente. → La consulta por `customerId` usa `Optional` y devuelve explícitamente `null` cuando no hay cliente, sin inicializar proxies.
- [Riesgo] Una asociación JPA sobre un `customer_id` sin fila podría crear un proxy que lance `EntityNotFoundException` al inicializarse y una resolución accidental podría marcar la transacción como rollback-only dentro de `TransactionTemplate`. → No existe asociación JPA; el adaptador consulta `CustomerJpaRepository.findById` directamente y no captura excepciones genéricas.
- [Riesgo] El cambio de esquema V4 puede fallar si una base ya contiene datos incompatibles con `customers`. → La migración solo crea la tabla sin validar ni imponer referencias sobre pedidos existentes.

## Migration Plan

1. Aplicar V4 para crear `customers` sin modificar filas existentes de `orders`.
2. Desplegar el código que añade la relación de lectura y el endpoint.
3. Verificar el endpoint con estados válidos, inválidos, ausencia de resultados y referencias de cliente huérfanas.
4. Rollback: retirar el endpoint y los componentes de lectura; no borrar la tabla `customers` automáticamente para evitar pérdida de datos introducidos por otros procesos.
