# Proposal

## Why

Hoy el sistema solo tiene el catálogo de productos: no se pueden crear pedidos ni reservar stock. Sin un módulo de inventario y otro de pedidos con reserva atómica, no es posible vender nada de lo que el catálogo publica.

## What Changes

- Nuevo módulo **inventory**: stock por producto, reservable de forma condicional y atómica (todo o nada) mediante una fachada Java pública en el mismo proceso. El stock de prueba se carga solo en tests; no hay API de gestión de stock.
- Nuevo módulo **ordering**: creación de pedidos vía `POST /api/orders`. El cliente se identifica con la cabecera `X-Customer-Id` (UUID). El pedido nace `PENDING` y el 201 devuelve `id`, `customerId`, `status` y `lines` (sin cabecera `Location`).
- Rechazos: cabecera ausente → 400 `missing-customer-id`; cabecera no UUID → 400 `invalid-customer-id`; pedido sin líneas → 422 `empty-order`; cantidad ≤ 0 → 422 `invalid-quantity`; producto repetido en dos líneas → 422 `duplicate-product`; producto sin registro de stock → 422 `unknown-product`; stock insuficiente → 409 `insufficient-stock` (todo o nada). Precedencia si hay varios errores: cabecera (400) antes que cuerpo (422) antes que stock (409).
- Concurrencia: dos pedidos concurrentes por la última unidad → solo uno se crea y reserva.
- Errores de negocio con ProblemDetail (RFC 9457) cuyo `type` es una URI con base `https://api.master-acdc.dev/problems/<slug>` (el catálogo existente ya usa ProblemDetail).
- La liberación de stock queda diseñada como futura operación de la fachada `inventory.api`; sin clases de eventos ni cancelación en este change.
- Estados `PAID` y `SHIPPED` existen en el dominio de pedidos pero no se alcanzan por API en este change.

## Non-goals

- Precios y totales.
- Gestión de stock por API (el stock de prueba se carga en tests).
- Pagos.
- Envíos.
- Autenticación (simplificación: solo cabecera `X-Customer-Id`).
- Cancelación de pedidos (otro change).

## Capabilities

### New Capabilities

- `inventory`: reserva atómica de stock por producto (todo o nada, actualización condicional, sin negativos, producto desconocido como resultado propio) como fachada pública para otros módulos.
- `ordering`: creación de pedidos de cliente con validación de líneas, identificación por `X-Customer-Id` y mapeo de errores de negocio a HTTP con ProblemDetail.

### Modified Capabilities

<!-- ninguna: openspec/specs está vacío hoy; el catálogo no cambia de requisitos -->

## Impact

- Código nuevo: paquetes `com.codeja.master_acdc.inventory` y `com.codeja.master_acdc.ordering` (domain/application/infrastructure); el catálogo existente no se mueve.
- API HTTP nueva bajo el control de `ordering` (`POST /api/orders`) con nuevos ProblemDetail types (URIs).
- Base de datos: nuevas tablas Flyway para stock y pedidos/líneas (solo esquema; los datos de prueba de stock se cargan con `@Sql` en tests).
- Integración: solo `ordering.infrastructure` depende de la fachada Java de `inventory.api` (mismo proceso); `inventory` no depende de `ordering`. Transacción local con límite en un decorador de `ordering.infrastructure.config`.
- Tests: unitarios, `@WebMvcTest` e integración con Testcontainers (concurrencia incluida).
