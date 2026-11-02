# Design

## Context

El catálogo ya tiene adaptadores Mongo para búsqueda, detalle, creación y edición. El job `CatalogStockProjectionJob` sigue consultando stock con `NamedParameterJdbcTemplate`. Las tablas de catálogo se crearon en V1, V5 y V6; inventory y ordering también comparten la base PostgreSQL y no deben perder sus tablas. Véase `proposal.md` para el motivo y el alcance.

## Goals / Non-Goals

**Goals:**

- Terminar el retiro de PostgreSQL solo como almacenamiento del catálogo, dejando stock e ordering intactos.
- Mantener los contratos HTTP ya definidos en `product-search` y `product-editing`, incluidos status y `ProblemDetail.type`.
- Mantener las pruebas de eficiencia equivalentes sobre MongoDB y los límites arquitectónicos verificables.

**Non-Goals:**

- Cambiar el esquema o las consultas de inventory/ordering.
- Reemplazar el polling JDBC del stock por eventos, outbox o lecturas por producto.
- Añadir nuevas capacidades HTTP; el comportamiento público no cambia.

## Decisions

### V7 como migración contract

Agregar V7 con `DROP TABLE` en orden de dependencias: `reviews`, `product_attributes`, `product_variants`, `products` y finalmente `categories`, usando `IF EXISTS` si encaja con las convenciones de Flyway existentes. No ejecutar `clean`, ni borrar el historial de Flyway. V7 no nombra ni modifica `stock`, `orders`, `order_lines` ni tablas de clientes.

Esto es deliberadamente irreversible respecto de los datos PostgreSQL del catálogo: MongoDB ya es la fuente de verdad tras expandir-contraer. Antes de desplegar V7 se debe verificar que Mongo conserva productos, categorías, variantes, atributos y reseñas requeridos; no hay rollback que recupere filas borradas de PostgreSQL.

### Mongo único adaptador de catálogo

Eliminar la selección condicional `catalog.store`, todos los adaptadores y entidades de catálogo JPA, `ProductSpecifications`, sus mappers, y las piezas de migración PostgreSQL→Mongo (servicio, checkpoint, job, configuración exclusiva y pruebas asociadas). Eliminar propiedades del selector de producción y de test. Mantener el job de proyección de stock y su consulta parametrizada por lotes con JDBC: es el único acceso PostgreSQL que queda dentro de la infraestructura del catálogo y solo lee la tabla `stock`.

La suite dual del catálogo pasa a tener una única implementación Mongo: conservar las pruebas Mongo, adaptar la base de tests donde hoy se duplica el escenario y retirar las subclases Postgres. Conservar `ProductSearchMigrationIT` y `ProductDetailMigrationIT` como pruebas históricas de Flyway V5 y V6 respectivamente; fijar sus objetivos para que no ejecuten V7.

### Tests Spring e infraestructura Mongo global

Fase 0, realizada por una persona antes de cualquier cambio del agente: retirar del árbol de tests y de `scripts/protected-tests.txt` los cinco guardianes JPA indicados en proposal.md y borrar `src/test/resources/config/application.properties`, que era la decisión humana temporal de usar PostgreSQL en esos contextos.

Para el resto de los tests, proporcionar un singleton `MongoDBContainer` mediante una extensión JUnit 5 global registrada en `META-INF/services/org.junit.jupiter.api.extension.Extension`. Activar el descubrimiento automático con `junit.jupiter.extensions.autodetection.enabled=true` en `src/test/resources/junit-platform.properties`. La extensión arranca el contenedor y publica su URI mediante la propiedad de sistema `spring.mongodb.uri` antes de crear contextos Spring. Así no se editan clases `@SpringBootTest`, incluidos `StockReservationConcurrencyTest` y `StockReservationTransactionIT`. Limpiar las colecciones del catálogo por clase donde la clase reutilice contexto o datos persistentes.

- **Extensión global compartida (elegida):** no requiere cambios repetitivos en las anotaciones de tests ni inicializadores especiales por módulo, y ejercita el mismo autoconfigurado Mongo en todos los contextos. Costes: Docker arranca también para tests de inventory/ordering; el estado Mongo compartido exige limpieza por clase y evitar interferencias de ejecución paralela.
- **Inicializador desactivable fuera del catálogo (alternativa descartada):** reduce el arranque de Mongo para tests de inventory/ordering, pero añade una propiedad de activación y condicionales a la configuración de runtime, y puede hacer que los tests validen un contexto distinto al real.

### ArchUnit y reglas de arquitectura

Añadir las prohibiciones de dependencias desde cualquier paquete `..catalog..` hacia `jakarta.persistence..` y `org.springframework.data.jpa..` en `com.codeja.master_acdc.arch.CatalogMongoArchitectureTest`. `CatalogArchitectureTest` es guardián y no se modifica. Mantener permitido JDBC en la implementación del job de stock y Mongo en los adaptadores de infraestructura. No introducir dependencia JPA en domain/application ni relajar reglas actuales.

## Reglas de arquitectura

- `catalog.domain` no depende de Spring, Jakarta Persistence, Spring Data JPA, Mongo ni JDBC; sus puertos permanecen en domain.
- `catalog.application` depende solo de `catalog.domain` y no conoce infraestructura.
- Solo `catalog.infrastructure.adapters.out.mongo` implementa persistencia del catálogo; las conversiones siguen en mappers dedicados de infraestructura.
- `catalog.infrastructure` no depende de Jakarta Persistence ni Spring Data JPA. El job de stock puede usar JDBC parametrizado, sin tipos de inventory ni consultas dentro de un bucle por producto.
- Ordering conserva la dependencia sobre la API pública de inventory; inventory no depende de ordering.
- `CatalogMongoArchitectureTest` verifica las prohibiciones JPA del catálogo; `CatalogArchitectureTest` y los demás guardianes de arquitectura permanecen intactos. Se conservan las fronteras hexagonales existentes y la regla de que catalog no dependa de inventory.

## Risks / Trade-offs

- [V7 elimina datos residuales de catálogo en PostgreSQL] → verificar la colección Mongo autoritativa antes del despliegue; registrar snapshot/backup operacional previo al contrato.
- [La carga Mongo para tests de módulos no relacionados ralentiza la suite o comparte estado] → arrancar un único contenedor de test, aislar colecciones/datos y ejecutar pruebas relevantes en el fast loop.
- [Tests de migración V5/V6 se ven afectados por V7] → fijar explícitamente su target histórico y comprobar que el target no aplica V7.
- [Fallo o estado compartido en extensión JUnit global] → probar registro/autodetección y limpieza de colecciones en la suite completa; no cambiar las clases `@SpringBootTest`.
- [La proyección de stock queda eventualmente consistente] → conservar job por lotes JDBC y sus tests; el cambio no altera el retraso ni el origen de verdad del inventario.

## Migration Plan

0. **Fase humana obligatoria, antes de toda tarea del agente:** retirar del árbol y de `scripts/protected-tests.txt` ProductEditConflictTest, ProductEditingIT, ProductSearchQueryCountTest, ProductSearchPaginationTest y ProductDetailQueryCountTest; borrar `src/test/resources/config/application.properties`. Verificar el punto de relevo con `bash ./verify-quality.sh --fast`.
1. El agente elimina la ruta de persistencia JPA y migración PostgreSQL→Mongo; conserva únicamente las pruebas de Mongo y el job JDBC de stock. Fijar `ProductSearchMigrationIT`/`ProductDetailMigrationIT` a V5/V6.
2. Consolidar la suite de catálogo en Mongo y retirar subclases Postgres; mantener los contratos HTTP y pruebas de eficiencia Mongo.
3. Añadir la extensión JUnit global y el singleton MongoDBContainer; comprobar que contexts inventory/ordering cargan sin editar sus clases `@SpringBootTest` y que la limpieza por clase aísla los datos.
4. Añadir V7 y su integración, verificando que solo desaparezcan las cinco tablas de catálogo y stock/ordering sigan intactas.
5. Añadir reglas ArchUnit en `CatalogMongoArchitectureTest`, sin editar `CatalogArchitectureTest`.
6. Actualizar los deltas de specs para retirar y modificar los requisitos indicados en proposal.md, revisar escenarios de `product-search`/`product-detail` y cambiar la métrica de detalle donde corresponda.
7. Actualizar `docs/lab-s10.md`: antes — cuatro consultas PostgreSQL; después — un comando MongoDB; aclarar que la lectura JDBC por lotes del job de stock es aparte.
8. Ejecutar `bash ./verify-quality.sh --fast` y confirmar ArchUnit, tests HTTP con status/type, tests Mongo de conteo/paginación y persistencia intacta de inventory/ordering.

## Open Questions

Ninguna; el mecanismo de tests queda recomendado como contenedor Mongo compartido, con alternativa y trade-offs registrados arriba.
