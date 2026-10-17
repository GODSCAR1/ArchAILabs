# Design

## Context

Hoy solo existe el catálogo (`Product`/`Sku`) en `com.codeja.master_acdc.{domain,application,infrastructure}`: sin inventario ni pedidos, y los ProblemDetail del `ApiExceptionHandler` existente no emiten `type` URIs propios (el contexto de proyecto lo exige para cada error de negocio). La creación de pedidos requiere reservar stock de forma atómica y concurrente en el mismo proceso. Motivación y alcance: ver `proposal.md`; requisitos observables: ver `specs/inventory/spec.md` y `specs/ordering/spec.md`.

## Goals / Non-Goals

**Goals:**
- Reserva de stock atómica y concurrente (todo o nada) con stock nunca negativo.
- Creación de pedidos PENDING vía `POST /api/orders` identificando al cliente por `X-Customer-Id`.
- Fronteras de módulo claras (`inventory` y `ordering`) verificables con ArchUnit.
- Diseño del camino de liberación de stock para la futura cancelación, sin código en este change.

**Non-Goals:**
- Precios/totales, pagos, envíos, autenticación, cancelación de pedidos (otro change).
- API de gestión de stock: los datos de prueba de stock se cargan solo en tests.

## Decisions

1. **Módulos como paquetes bajo `com.codeja.master_acdc`**: `inventory` y `ordering`, cada uno con `{domain,application,infrastructure}`; el catálogo existente no se mueve. Sin JPMS hoy (no hay `module-info.java`), la modularidad es por paquetes + ArchUnit. *Alternativa descartada*: multi-módulo Maven — innecesaria para un mismo proceso y JVM.

2. **Integración por fachada Java de `inventory.api`**: la fachada pública de reserva vive en el paquete `inventory.api`; `ordering` la invoca por Java directa, no por HTTP ni eventos (mismo proceso, misma transacción local). La fachada envuelve la reserva en su propio `TransactionTemplate` con propagación REQUIRED: se une a la transacción del pedido cuando existe y, cuando se llama sola (p. ej. `InventoryFacadeIT`), abre la suya, de modo que la reserva es todo o nada también sin pedido. Solo `ordering.infrastructure` (adapters y config) importa `inventory.api`. `inventory` nunca depende de `ordering`.

3. **Reserva con comprobación de existencia y actualización condicional + CHECK**: la reserva comprueba primero que **todos** los productos de las líneas tienen registro de stock y solo después aplica las actualizaciones condicionales; así el producto desconocido (422) gane siempre a la insuficiencia de stock (409), sin depender del orden de los ids. En la tabla de stock, `CHECK (available >= 0)`; la reserva descuenta con una actualización condicional por producto (`UPDATE ... WHERE product_id = ? AND available >= ?`, filas afectadas = 0 ⇒ insuficiencia). Líneas se procesan **ordenadas por producto** para evitar deadlocks entre reservas concurrentes. Todo o nada: si alguna línea falla (insuficiencia o producto desconocido), la reserva completa se revierte. *Alternativa descartada*: bloqueo pesimista con `SELECT ... FOR UPDATE` — más contención sin ventaja frente al update condicional.

4. **Límite de transacción: decorador con `TransactionTemplate` en `ordering.infrastructure.config`**: los casos de uso de `application` no gestionan `@Transactional`; un decorador ensamblado en la config de `ordering.infrastructure` envuelve el caso de uso con `TransactionTemplate`, de modo que la reserva en `inventory` (que se une con propagación REQUIRED, ver decisión 2) y la persistencia del pedido comparten la misma transacción local. Así `application` depende solo de `domain`.

5. **ProblemDetail con `type` = URI**: base `https://api.master-acdc.dev/problems/` + slug por error: `missing-customer-id` e `invalid-customer-id` (400), `empty-order`, `invalid-quantity`, `duplicate-product` y `unknown-product` (422), `insufficient-stock` (409). Precedencia cuando hay varios errores: cabecera (400) antes que cuerpo (422) antes que stock (409). La reserva de `inventory` (que no es HTTP) devuelve un resultado de dominio con tipos propios (insuficiencia y producto desconocido) que `ordering` traduce a sus ProblemDetail.

6. **Estados del pedido**: el dominio de `ordering` modela PENDING, PAID y SHIPPED; solo PENDING es alcanzable por API. Persistencia inicial del estado PENDING.

7. **Liberación de stock: solo diseño, sin clases en este change**: la futura cancelación liberará stock llamando a una **operación de la fachada `inventory.api`** desde el adaptador de ordering (no mediante un evento ni un puerto receptor en inventory), para no invertir la dependencia: `inventory` sigue sin conocer `ordering`. No se crean clases de evento ni de emisión en este change.

8. **Datos de stock de prueba solo en tests**: la migración Flyway crea únicamente el esquema; los inserts de stock para los escenarios se cargan con `@Sql` en los tests, nunca en migraciones de producción.

## Reglas de arquitectura (ArchUnit)

- `inventory.domain` no importa Spring, JPA ni Jackson.
- `inventory.application` depende solo de `inventory.domain`; nunca de `inventory.infrastructure` ni de `ordering`.
- `ordering.domain` no importa Spring, JPA ni Jackson.
- `ordering.application` depende solo de `ordering.domain`; nunca de `ordering.infrastructure` ni de `inventory` ni de `inventory.api`.
- Solo `ordering.infrastructure` (adapters y config) puede importar `inventory.api`; nada dentro de `inventory` importa `ordering` (dependencia unidireccional).
- `ApplicationContext.getBean()` y campos estáticos prohibidos fuera de `infrastructure/config`.

## Risks / Trade-offs

- [Update condicional por producto en orden distinto ⇒ deadlock] → procesar líneas siempre ordenadas por `product_id`.
- [Fachada Java acopla ordering e inventory en el mismo despliegue] → aceptable hoy; la liberación futura reutiliza la misma fachada, manteniendo la dependencia unidireccional.
- [CHECK + update condicional depende de PostgreSQL] → ya es el motor de Testcontainers y producción; sin abstracción extra.
- [Cobertura PIT ≥ 75%] → los servicios nuevos se prueban a nivel unitario e IT para no bajar el umbral.

## Migration Plan

Flyway crea las tablas nuevas (stock, pedidos, líneas); sin inserts de producción: los datos de prueba de stock se cargan con `@Sql` en los tests. Rollback: revert del cambio de código; las tablas nuevas son aditivas.

## Open Questions

<!-- ninguna: los tipos de error, estados, precedencia y límites de transacción ya están decididos -->
