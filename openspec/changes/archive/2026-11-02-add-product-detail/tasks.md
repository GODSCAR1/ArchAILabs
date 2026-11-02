# Tasks

## 1. Persistencia y modelo de lectura

- [x] 1.1 Crear la migración Flyway V6 con variantes, atributos y reseñas, sus claves, restricciones e índices por producto, incluyendo `reviews (product_id, created_at DESC, id)` y los índices de variantes y atributos por `product_id`, sin inserts; verificar con `ProductDetailMigrationIT` que la migración aplica y no crea datos semilla.
- [x] 1.2 Añadir los modelos de dominio/proyección para detalle, variante, atributo, valoración y reseña paginada, junto con sus puertos de salida; verificar invariantes y valores vacíos con `ProductDetailServiceTest`.
- [x] 1.3 Añadir `ProductDetailPersistenceMapper` sobre las filas nativas de las tres colecciones, incluyendo agregados de reseñas y orden fecha/id; verificar conversiones con `ProductDetailPersistenceMapperTest`.

## 2. Consulta de aplicación y adaptadores

- [x] 2.1 Implementar la consulta de ficha por SKU como cuatro lecturas fijas: producto/categoría/stock, variantes, atributos y reseñas agregadas con las cinco últimas; verificar `should_keep_query_count_at_most_four_when_detail_has_many_associations` en `ProductDetailQueryCountTest` con `@DisplayName("Muchas colecciones asociadas")`, 60 variantes, 60 atributos y 60 reseñas con `created_at` distintos (más que `default_batch_fetch_size=50`), `flush()`, `clear()` y `Statistics.clear()`, y assert exacto `isEqualTo(4)` para la línea base de S10.
- [x] 2.2 Implementar la lectura paginada de reseñas con `page=0`, `size=20`, normalización a `100` y orden `created_at DESC, id ASC`; verificar `should_order_reviews_by_created_at_and_id_when_dates_are_equal` y `should_normalize_size_to_one_hundred_when_size_is_too_large` en `ProductReviewQueryIT`.
- [x] 2.3 Ensamblar los puertos, servicios y adaptadores desde configuración de infraestructura, sin dependencias entre catalog e inventory; verificar `catalogDoesNotDependOnInventory`, `inventoryDoesNotDependOnCatalog` y `applicationDoesNotDependOnInfrastructure` en `CatalogArchitectureTest`.

## 3. API HTTP

- [x] 3.1 Añadir los DTOs, mappers web y endpoints `GET /api/products/{sku}` y `GET /api/products/{sku}/reviews`; verificar con `@WebMvcTest` los escenarios `Ficha existente con todos sus datos`, `Producto sin stock ni datos opcionales`, `Límite de reseñas recientes`, `Primera página de reseñas`, `Tamaño superior al máximo`, `Desempate de reseñas` y `Paginación de reseñas inválida`, usando `@DisplayName` exactos.
- [x] 3.2 Traducir SKU inexistente a `ProblemDetail` RFC 9457 con type `https://api.master-acdc.dev/problems/product-not-found`; verificar con `@WebMvcTest` los escenarios `SKU inexistente en ficha` y `SKU inexistente en reseñas`, usando `@DisplayName` exactos, HTTP `404` y type exacto.
- [x] 3.3 Comprobar que `PUT /api/products/{id}` continúa recibiendo UUID, que un SKU con forma de UUID se resuelve por GET y que el buscador no cambia; verificar `should_route_put_to_edition_and_get_to_detail_when_path_is_uuid_or_sku` en `ProductControllerTest` con `@DisplayName("Edición UUID y consulta SKU coexistentes")`, comprobando que el PUT llega a edición, que el GET del SKU con forma de UUID inexistente devuelve 404 y no 400, y que `GET /api/products` llega al buscador; ejecutar también las pruebas existentes de `ProductSearch`.

## 4. Integración y calidad

- [x] 4.1 Probar la respuesta completa contra PostgreSQL/Testcontainers, incluyendo categoría, stock cero, variantes, atributos, promedio, conteo y máximo de cinco reseñas; verificar los escenarios `Ficha existente con todos sus datos`, `Producto sin stock ni datos opcionales`, `Promedio con decimales`, `Variantes y atributos pertenecen al producto` y `Límite de reseñas recientes` mediante `@DisplayName` exactos.
- [x] 4.2 Probar paginación, total de reseñas, página vacía y producto inexistente contra PostgreSQL/Testcontainers; verificar los escenarios `Primera página de reseñas`, `Tamaño superior al máximo`, `Desempate de reseñas` y `SKU inexistente en reseñas` mediante `@DisplayName` exactos.
- [x] 4.3 Ejecutar `bash ./verify-quality.sh --full` y corregir cualquier fallo de specs, Spotless, Checkstyle o tests sin desactivar guardianes.
