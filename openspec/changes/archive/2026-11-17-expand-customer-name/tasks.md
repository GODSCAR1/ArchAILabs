# Tasks

## 1. Migración y comprobaciones

- [x] 1.1 Crear V11 transaccional con prevalidación y backfill sin alterar `name`; verificar con `CustomerNameMigrationIT` los escenarios `@DisplayName("Nombre histórico de una palabra")`, `@DisplayName("Nombre histórico con varias palabras")` y `@DisplayName("Caracteres y longitud límite se conservan")`, incluyendo tabulaciones y 120 caracteres.
- [x] 1.2 Verificar con `CustomerNameMigrationIT` los escenarios `@DisplayName("Nombre histórico vacío detiene la migración")` y `@DisplayName("Reintento tras corrección")`: recuento y mensaje, versión V10, ausencia de columnas nuevas, datos intactos y éxito tras corrección.
- [x] 1.3 Añadir los SELECT independientes en `src/main/resources/db/checks/` y verificar con `CustomerNameMigrationIT` los escenarios `@DisplayName("Comprobaciones de clientes coherentes")` y `@DisplayName("Comprobaciones detectan infracciones")`, incluyendo NULL y cada invariante por separado.

## 2. Escritura compatible

- [x] 2.1 Añadir el value object de partición en el dominio de ordering y cubrir con un test unitario los ejemplos de migración, espacios U+0020, tabulaciones y límites; contrastar sus resultados con `CustomerNameMigrationIT`.
- [x] 2.2 Adaptar entidad, mapper y adaptador para escribir las tres columnas y mantener la validación y `name`; verificar con tests unitarios del mapper y adaptador y con los escenarios de registro de la tarea 2.3.
- [x] 2.3 Añadir pruebas de registro real con `@DisplayName("Registro con nombre y espacios")` y `@DisplayName("Registro sin nombre explícito conserva el valor derivado")`, comprobando columnas, respuesta y todos los checks; ejecutar el IT correspondiente.
- [x] 2.4 Ajustar únicamente los fixtures de inserción directa que interfieran con comprobaciones de invariantes; verificar ejecutando los IT afectados, sin modificar guardianes protegidos sin decisión humana.

## 3. Esquema y verificación integrada

- [x] 3.1 Crear `SchemaIT` extendiendo `IntegrationTest` y usando una base aislada en el PostgreSQL común, recreada con `DROP DATABASE IF EXISTS ... WITH (FORCE)` y `CREATE DATABASE` y eliminada al terminar, sin contenedor propio; migrar la base vacía desde cero, comprobar historial sin fallos ni pendientes y validar todas las entidades JPA contra esa misma base; verificar `@DisplayName("Esquema nuevo coherente")` ejecutando el IT.
- [x] 3.2 Ejecutar explícitamente cada `*IT` añadido o modificado con el comando Failsafe exigido por `AGENTS.md`, además de los tests unitarios y de arquitectura afectados; comprobar sus resultados.
- [x] 3.3 Ejecutar `bash ./mvnw spotless:apply` y `bash ./verify-quality.sh --fast` con timeout mínimo de 600 s; comprobar el resultado y comunicar cualquier bloqueo sin desactivar verificaciones.
