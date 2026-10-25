# Proposal

## Why

El módulo de pedidos puede almacenar estados posteriores como `PAID`, pero todavía no ofrece una consulta operativa para listar esos pedidos. Un endpoint de solo lectura permitirá consultar el cliente y métricas básicas de cada pedido sin alterar los flujos existentes de creación y cancelación.

## What Changes

- Añadir `GET /api/orders?status=PAID`, con un resumen por pedido: `id`, nombre del cliente, número de líneas y total de unidades.
- Crear la tabla de solo lectura `customers` mediante una migración V4, sin clave foránea desde `orders.customer_id`.
- Añadir una entidad y un repositorio de clientes de solo lectura; resolver el nombre por `customerId`, sin asociación JPA desde el pedido y manteniendo la carga EAGER de las líneas.
- Añadir un caso de uso de listado por estado, un puerto de consulta y un adaptador de persistencia que mapee las entidades existentes a resúmenes.
- Ejecutar la consulta dentro de una transacción de solo lectura configurada en ordering.
- Rechazar `status` inválido o ausente con `400 ProblemDetail`; usar `https://api.master-acdc.dev/problems/invalid-order-status` para valores inválidos, incluidos nombres en minúsculas, y `https://api.master-acdc.dev/problems/missing-order-status` cuando falta el parámetro.
- Devolver `customerName: null` cuando `customer_id` no tenga fila correspondiente en `customers`, sin convertir ese caso en error.
- No cambiar los flujos de crear/cancelar pedidos ni optimizar todavía las consultas; esta versión será la línea base de medición.

## Non-goals

- No añadir creación, actualización ni administración de clientes.
- No modificar la API de creación o cancelación de pedidos.
- No introducir paginación, ordenación, filtros adicionales ni optimizaciones de consulta en esta versión.
- No añadir una clave foránea entre pedidos y clientes.

## Capabilities

### New Capabilities

- Ninguna.

### Modified Capabilities

- `ordering`: Añade la consulta de pedidos por estado y sus respuestas y rechazos HTTP.

## Impact

- API HTTP de ordering: nuevo endpoint de lectura y nuevos `ProblemDetail` para el filtro.
- Persistencia: nueva migración de clientes y entidad y repositorio de clientes de solo lectura, sin asociación JPA desde pedidos.
- Dominio y aplicación: nuevo resumen de pedido, puerto de consulta y caso de uso.
- Pruebas: unitarias para el caso de uso y pruebas web e integración con PostgreSQL/Testcontainers para los escenarios del listado.
