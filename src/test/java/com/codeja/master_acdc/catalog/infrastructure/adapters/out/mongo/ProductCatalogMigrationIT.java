package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.model.Category;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.bson.UuidRepresentation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ProductCatalogMigrationIT {

  private static final UUID CATEGORY_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID PRODUCT_ID = UUID.fromString("40000000-0000-0000-0000-000000000010");
  private static final UUID OVERSIZED_ID = UUID.fromString("40000000-0000-0000-0000-000000000020");
  private static final UUID NEXT_PRODUCT_ID =
      UUID.fromString("40000000-0000-0000-0000-000000000030");
  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  private static JdbcTemplate jdbcTemplate;
  private static MongoClient mongoClient;
  private static MongoTemplate mongoTemplate;
  private CatalogProductMigrationService migrationService;

  @BeforeAll
  static void initialize_databases() {
    DataSource dataSource = dataSource();
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    jdbcTemplate = new JdbcTemplate(dataSource);
    mongoClient =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build());
    mongoTemplate = new MongoTemplate(mongoClient, "catalog_migration_test");
    new CatalogIndexInitializer(mongoTemplate).initialize();
  }

  @AfterAll
  static void close_mongo_client() {
    mongoClient.close();
  }

  @BeforeEach
  void reset_databases() {
    jdbcTemplate.update("delete from reviews");
    jdbcTemplate.update("delete from product_attributes");
    jdbcTemplate.update("delete from product_variants");
    jdbcTemplate.update("delete from stock");
    jdbcTemplate.update("delete from products");
    jdbcTemplate.update("delete from categories where id <> ?", Category.UNCATEGORIZED_ID);
    mongoTemplate.getCollection("catalog_products").deleteMany(new org.bson.Document());
    mongoTemplate.getCollection("reviews").deleteMany(new org.bson.Document());
    mongoTemplate
        .getCollection("catalog_migration_checkpoints")
        .deleteMany(new org.bson.Document());
    migrationService =
        new CatalogProductMigrationService(
            new NamedParameterJdbcTemplate(jdbcTemplate), mongoTemplate);
  }

  @Test
  @DisplayName("Copia de stock y categoría")
  void should_copy_current_stock_and_category_when_migrating_batch() {
    seedProduct(PRODUCT_ID, "SKU-COPY", "Zapato", "Calzado", 27);

    migrationService.migrateBatch(null, 10);

    CatalogProduct document = mongoTemplate.findById(PRODUCT_ID, CatalogProduct.class);
    assertThat(document.category().name()).isEqualTo("Calzado");
    assertThat(document.availableStock()).isEqualTo(27);
  }

  @Test
  @DisplayName("Ficha agregada disponible")
  void should_materialize_product_and_aggregate_fields_when_migrating_batch() {
    seedProduct(PRODUCT_ID, "SKU-FULL", "Zapato", "Calzado", 27);
    insertVariant(PRODUCT_ID, UUID.fromString("40000000-0000-0000-0000-000000000101"), 1);
    jdbcTemplate.update(
        "insert into product_attributes (product_id, name, value) values (?, ?, ?)",
        PRODUCT_ID,
        "z-material",
        "Cuero");
    jdbcTemplate.update(
        "insert into product_attributes (product_id, name, value) values (?, ?, ?)",
        PRODUCT_ID,
        "a-origen",
        "Colombia");
    insertSixReviews(PRODUCT_ID);

    migrationService.migrateBatch(null, 10);

    CatalogProduct document = mongoTemplate.findById(PRODUCT_ID, CatalogProduct.class);
    assertThat(document.sku()).isEqualTo("SKU-FULL");
    assertThat(document.price()).isEqualTo(189900);
    assertThat(document.variants()).hasSize(1);
    assertThat(document.attributes()).containsEntry("z-material", "Cuero");
    assertThat(document.attributes().keySet()).containsExactly("a-origen", "z-material");
    assertThat(document.rating().average()).isEqualByComparingTo("4.17");
    assertThat(document.rating().count()).isEqualTo(6);
    assertThat(document.latestReviews()).hasSize(5);
  }

  @Test
  @DisplayName("Arrays acotados")
  void should_reject_product_and_continue_batch_when_variant_limit_is_exceeded() {
    seedProduct(OVERSIZED_ID, "SKU-TOO-MANY", "Muchos", "Calzado", 1);
    seedProduct(NEXT_PRODUCT_ID, "SKU-NEXT", "Siguiente", "Calzado", 2);
    mongoTemplate.insert(
        new CatalogProduct(
            OVERSIZED_ID,
            "SKU-TOO-MANY",
            "Documento obsoleto",
            189900,
            com.codeja.master_acdc.catalog.domain.model.ProductStatus.ACTIVE,
            CREATED_AT,
            0,
            new CategoryRef(CATEGORY_ID, "Calzado"),
            List.of(),
            new LinkedHashMap<>(Map.of("color", "azul")),
            1,
            new RatingSummary(java.math.BigDecimal.ZERO.setScale(2), 0),
            List.of()));
    for (int index = 0; index < 101; index++) {
      insertVariant(OVERSIZED_ID, UUID.randomUUID(), index);
    }

    var result = migrationService.migrateBatch(null, 10);

    assertThat(result.rejectedSkus()).containsExactly("SKU-TOO-MANY");
    assertThat(mongoTemplate.findById(OVERSIZED_ID, CatalogProduct.class)).isNull();
    assertThat(mongoTemplate.findById(NEXT_PRODUCT_ID, CatalogProduct.class)).isNotNull();
  }

  @Test
  @DisplayName("Reanudación tras interrupción")
  void should_resume_after_last_confirmed_id_when_migration_is_interrupted() {
    seedProduct(PRODUCT_ID, "SKU-FIRST", "Primero", "Calzado", 1);
    seedProduct(NEXT_PRODUCT_ID, "SKU-SECOND", "Segundo", "Calzado", 2);

    var firstBatch = migrationService.migrateNextBatch(1);
    var checkpointAfterFirst =
        mongoTemplate.findById("catalog-products", CatalogMigrationCheckpoint.class);
    var resumedBatch = migrationService.migrateNextBatch(1);

    assertThat(firstBatch.lastProcessedId()).isEqualTo(PRODUCT_ID);
    assertThat(checkpointAfterFirst.state()).isEqualTo(MigrationState.EN_CURSO);
    assertThat(resumedBatch.lastProcessedId()).isEqualTo(NEXT_PRODUCT_ID);
    assertThat(migrationService.migrateNextBatch(1).complete()).isTrue();
  }

  @Test
  @DisplayName("Ejecución repetida")
  void should_keep_products_and_reviews_idempotent_when_migration_is_repeated() {
    seedProduct(PRODUCT_ID, "SKU-REPEAT", "Repetido", "Calzado", 3);
    insertSixReviews(PRODUCT_ID);

    migrationService.migrateAll(10);
    migrationService.migrateAll(10);

    assertThat(mongoTemplate.getCollection("catalog_products").countDocuments()).isEqualTo(1);
    assertThat(mongoTemplate.getCollection("reviews").countDocuments()).isEqualTo(6);
  }

  @Test
  @DisplayName("Informe de migración")
  void should_report_counts_rejections_and_checkpoint_when_migration_finishes() {
    seedProduct(OVERSIZED_ID, "SKU-REJECTED", "Muchos", "Calzado", 1);
    seedProduct(NEXT_PRODUCT_ID, "SKU-ACCEPTED", "Aceptado", "Calzado", 2);
    for (int index = 0; index < 101; index++) {
      insertVariant(OVERSIZED_ID, UUID.randomUUID(), index);
    }

    CatalogMigrationReport report = migrationService.migrateAll(10);

    assertThat(report.productsProcessed()).isEqualTo(1);
    assertThat(report.rejectedSkus()).containsExactly("SKU-REJECTED");
    assertThat(report.state()).isEqualTo(MigrationState.COMPLETADO);
    assertThat(report.lastProcessedId()).isEqualTo(NEXT_PRODUCT_ID);
  }

  @Test
  @DisplayName("Ciclo siguiente tras un barrido completo")
  void should_restart_from_first_id_when_next_cycle_follows_completion() {
    seedProduct(PRODUCT_ID, "SKU-CHANGED", "Cambiado", "Calzado", 3);
    migrationService.migrateAll(10);
    jdbcTemplate.update("update stock set available = 32 where product_id = ?", PRODUCT_ID);
    jdbcTemplate.update("update products set price = 210000 where id = ?", PRODUCT_ID);
    jdbcTemplate.update("update categories set name = 'Zapatos' where id = ?", CATEGORY_ID);

    migrationService.migrateAll(10);

    CatalogProduct document = mongoTemplate.findById(PRODUCT_ID, CatalogProduct.class);
    assertThat(document.category().name()).isEqualTo("Zapatos");
    assertThat(document.availableStock()).isEqualTo(32);
    assertThat(document.price()).isEqualTo(210000);
  }

  private static DataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    return dataSource;
  }

  private void seedProduct(UUID id, String sku, String name, String categoryName, int stock) {
    jdbcTemplate.update(
        "insert into categories (id, name, version) values (?, ?, 0) "
            + "on conflict (id) do update set name = excluded.name",
        CATEGORY_ID,
        categoryName);
    jdbcTemplate.update(
        "insert into products (id, name, sku, price, category_id, status, created_at, version) "
            + "values (?, ?, ?, 189900, ?, 'ACTIVE', ?, 0)",
        id,
        name,
        sku,
        CATEGORY_ID,
        Timestamp.from(CREATED_AT));
    jdbcTemplate.update("insert into stock (product_id, available) values (?, ?)", id, stock);
  }

  private void insertVariant(UUID productId, UUID id, int index) {
    jdbcTemplate.update(
        "insert into product_variants "
            + "(id, product_id, sku_suffix, color, size, additional_price) "
            + "values (?, ?, ?, 'Azul', 'M', ?)",
        id,
        productId,
        "-" + index,
        index);
  }

  private void insertSixReviews(UUID productId) {
    for (int index = 1; index <= 6; index++) {
      jdbcTemplate.update(
          "insert into reviews (id, product_id, author, rating, comment, created_at) "
              + "values (?, ?, 'Ana', ?, ?, ?)",
          UUID.randomUUID(),
          productId,
          reviewRating(index),
          "Review " + index,
          Timestamp.from(CREATED_AT.plusSeconds(index)));
    }
  }

  private int reviewRating(int index) {
    return switch (index) {
      case 1 -> 4;
      case 2 -> 5;
      case 3 -> 3;
      case 6 -> 5;
      default -> 4;
    };
  }
}
