# Tasks

## 0. Fase humana — antes del agente

- [ ] 0.1 **PERSONA — no marcar como completada por el agente.** Retirar ProductEditConflictTest, ProductEditingIT, ProductSearchQueryCountTest, ProductSearchPaginationTest y ProductDetailQueryCountTest del árbol y de `scripts/protected-tests.txt`; borrar `src/test/resources/config/application.properties`. Verificación humana: `bash ./verify-quality.sh --fast` pasa y los guardianes Mongo quedan intactos. El agente se detiene aquí hasta que una persona complete la fase.

## 1. Retirar persistencia PostgreSQL de catálogo

- [ ] 1.1 Eliminar adaptadores, entidades, repositorios, mappers, `ProductSpecifications` y configuración JPA del catálogo, y retirar `catalog.store`; verificar compilación y que ninguna ruta productiva del catálogo usa JPA.
- [ ] 1.2 Retirar el servicio, checkpoint, job y pruebas de migración PostgreSQL→Mongo; conservar `CatalogStockProjectionJob` con consulta JDBC parametrizada por lotes; verificar `CatalogStockProjectionJobIT` con `@DisplayName("Actualización periódica de stock")` y `@DisplayName("Producto sin fila de stock")`.

## 2. Suite de catálogo Mongo

- [ ] 2.1 Consolidar las suites duales en Mongo y retirar las subclases PostgreSQL; verificar los escenarios HTTP existentes de `product-search`, `product-editing` y `product-detail`, manteniendo status y `ProblemDetail.type` según sus `@DisplayName` actuales. En `ProductDetailMongoIT`, verificar el escenario `@DisplayName("Respuesta HTTP de ficha")`; verificar el coste de ficha con `ProductDetailQueryCountMongoTest`, que lleva `@DisplayName("Muchas colecciones asociadas")` y comprueba una sola operación de lectura.
- [ ] 2.2 Conservar `ProductSearchMigrationIT` y `ProductDetailMigrationIT` como verificaciones históricas con target Flyway V5 y V6 respectivamente; verificar que ninguna aplica V7.

## 3. Infraestructura de tests y contrato de datos

- [ ] 3.1 Registrar una extensión JUnit 5 global en `META-INF/services/org.junit.jupiter.api.extension.Extension`, activar autodetección con `junit.jupiter.extensions.autodetection.enabled=true` en `src/test/resources/junit-platform.properties`, arrancar un `MongoDBContainer` singleton y publicar `spring.mongodb.uri` como propiedad de sistema antes de crear contextos Spring. Limpiar colecciones del catálogo por clase cuando haga falta. Verificar que inventory y ordering arrancan sin editar sus clases `@SpringBootTest`, incluidos `StockReservationConcurrencyTest` y `StockReservationTransactionIT`.
- [ ] 3.2 Crear V7 que borre `reviews`, `product_attributes`, `product_variants`, `products` y `categories`, sin alterar stock ni tablas de ordering; verificar con integración PostgreSQL que las cinco tablas desaparecen y stock/ordering permanecen.
- [ ] 3.3 Verificar el recorrido de migración hasta V7 con un target explícito y aserciones de tablas pre/post; ejecutar el test contra PostgreSQL Testcontainers.

## 4. Límites arquitectónicos

- [ ] 4.1 Añadir las reglas de ausencia de dependencias `jakarta.persistence..` y `org.springframework.data.jpa..` en `com.codeja.master_acdc.arch.CatalogMongoArchitectureTest`; no editar el guardián `CatalogArchitectureTest`. Verificar las reglas ArchUnit y conservar las reglas existentes de fronteras hexagonales.

## 5. Documentación e integración

- [ ] 5.1 Crear `docs/lab-s10.md` con ficha antes/después: cuatro consultas PostgreSQL frente a un comando MongoDB; aclarar que la proyección JDBC por lotes de stock es aparte. Verificar que la ficha coincide con los tests de conteo.
- [ ] 5.2 Ejecutar `bash ./verify-quality.sh --fast`; verificar ArchUnit, tests HTTP con status/type, tests Mongo de conteo/paginación y que stock y ordering siguen persistiendo sin cambios.

## Workflow follow-up

- Una persona confirma backup y catálogo autoritativo en Mongo antes de desplegar V7, ya que borra irreversiblemente las tablas de catálogo PostgreSQL.
- La persona revisa y archiva/sincroniza OpenSpec según el proceso del proyecto; no hacerlo automáticamente.
