# Proposal

## Why

El nombre del cliente se almacena en un único campo. El paso 4 del lab S16 necesita preparar su separación sin romper las lecturas ni perder datos históricos y comprobar las migraciones con datos reales.

## What Changes

- Añadir mediante V11 los campos de nombre y apellido y rellenarlos a partir del nombre existente, sin cambiar este último.
- Mantener la API y escribir simultáneamente las tres representaciones al registrar clientes, con una única regla de partición en el dominio de ordering.
- Detener y revertir V11 con un recuento claro cuando haya nombres históricos vacíos o solo con espacios, sin inventar valores.
- Añadir consultas independientes de invariantes y pruebas de esquema, migración con datos, rollback y registro nuevo.

## Capabilities

### New Capabilities

- `customer-name`: representación compatible del nombre de cliente, migración y comprobación de invariantes.

### Modified Capabilities

- Ninguna: el contrato público de registro y el listado de pedidos se conservan.

## Impact

- Migración Flyway V11, dominio y persistencia de ordering, registro de identity y fixtures que insertan clientes directamente.
- Nuevos checks SQL y pruebas de integración con bases de datos aisladas en el servidor PostgreSQL compartido.
- Sin cambios de API ni dependencias nuevas.

## Non-goals

- Dejar de leer o escribir `name`, eliminarlo o desplegar la fase contraer.
- Corregir automáticamente datos históricos inválidos o sustituirlos por valores ficticios.
- Cambiar la política de validación de registro o el modelo de autenticación.
