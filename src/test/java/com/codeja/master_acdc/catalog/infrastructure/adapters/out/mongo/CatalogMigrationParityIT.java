package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductDetailRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductJpaRepository;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence.ProductSearchRepositoryAdapter;
import com.codeja.master_acdc.catalog.infrastructure.config.CatalogMongoTestConfiguration;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "catalog.store=postgres")
@Testcontainers
@Import(CatalogMongoTestConfiguration.class)
class CatalogMigrationParityIT {

  private static final UUID CATEGORY_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
  private static final int PRODUCTS = 200;
  private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Container @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  @Autowired MongoTemplate mongoTemplate;
  @Autowired EntityManager entityManager;
  @Autowired ProductJpaRepository productJpaRepository;

  private ProductDetailRepositoryAdapter postgresDetails;
  private CatalogDetailMongoRepositoryAdapter mongoDetails;
  private ProductSearchRepositoryAdapter postgresSearch;
  private ProductSearchMongoRepositoryAdapter mongoSearch;

  @BeforeEach
  void prepare_both_stores() {
    Flyway.configure()
        .dataSource(jdbcTemplate.getDataSource())
        .locations("classpath:db/migration")
        .load()
        .migrate();
    clearPostgres();
    clearMongo();
    new CatalogIndexInitializer(mongoTemplate).initialize();
    insertProducts();
    new CatalogProductMigrationService(namedParameterJdbcTemplate, mongoTemplate).migrateAll(100);
    postgresDetails = new ProductDetailRepositoryAdapter(entityManager);
    mongoDetails = new CatalogDetailMongoRepositoryAdapter(mongoTemplate);
    postgresSearch = new ProductSearchRepositoryAdapter(productJpaRepository);
    mongoSearch = new ProductSearchMongoRepositoryAdapter(mongoTemplate);
  }

  @Test
  @DisplayName("Recuentos equivalentes")
  void should_match_product_variant_and_review_counts_when_migration_completes() {
    long postgresProducts =
        jdbcTemplate.queryForObject("select count(*) from products", Long.class);
    long postgresVariants =
        jdbcTemplate.queryForObject("select count(*) from product_variants", Long.class);
    long postgresReviews = jdbcTemplate.queryForObject("select count(*) from reviews", Long.class);
    new CatalogProductMigrationService(namedParameterJdbcTemplate, mongoTemplate).migrateAll(100);
    List<CatalogProduct> documents = mongoTemplate.findAll(CatalogProduct.class);

    assertThat(documents).hasSize((int) postgresProducts);
    assertThat(documents.stream().mapToLong(product -> product.variants().size()).sum())
        .isEqualTo(postgresVariants);
    assertThat(mongoTemplate.getCollection("reviews").countDocuments()).isEqualTo(postgresReviews);
  }

  @Test
  @DisplayName("Fichas equivalentes")
  void should_match_two_hundred_random_product_details_when_migration_completes() {
    List<Integer> indexes = new java.util.ArrayList<>();
    for (int index = 0; index < PRODUCTS; index++) {
      indexes.add(index);
    }
    Collections.shuffle(indexes, new Random(75));

    indexes.forEach(
        index -> {
          String sku = sku(index);
          assertThat(mongoDetails.findBySku(sku)).isEqualTo(postgresDetails.findBySku(sku));
        });
  }

  @Test
  @DisplayName("Buscador equivalente")
  void should_match_search_pages_and_literal_text_when_searching_both_stores() {
    assertSearchMatches(criteria(null, "name"));
    assertSearchMatches(criteria("a.b", "name"));
    assertSearchMatches(criteria("(", "name"));
    assertSearchMatches(criteria("product", "price"));
  }

  private void assertSearchMatches(ProductSearchCriteria criteria) {
    CatalogMongoTestConfiguration.resetReadCommands();
    assertThat(mongoSearch.search(criteria)).isEqualTo(postgresSearch.search(criteria));
    assertThat(CatalogMongoTestConfiguration.readCommands()).isEqualTo(2);
  }

  private ProductSearchCriteria criteria(String text, String sortField) {
    return new ProductSearchCriteria(
        text,
        CATEGORY_ID,
        null,
        null,
        Set.of(ProductStatus.ACTIVE),
        false,
        0,
        PRODUCTS,
        new ProductSearchOrder(sortField, ProductSearchOrder.Direction.ASC));
  }

  private void clearPostgres() {
    jdbcTemplate.update("delete from reviews");
    jdbcTemplate.update("delete from product_attributes");
    jdbcTemplate.update("delete from product_variants");
    jdbcTemplate.update("delete from stock");
    jdbcTemplate.update("delete from products");
    jdbcTemplate.update("delete from categories where id = ?", CATEGORY_ID);
  }

  private void clearMongo() {
    mongoTemplate.remove(new Query(), CatalogProduct.class);
    mongoTemplate.remove(new Query(), CatalogReview.class);
    mongoTemplate.remove(new Query(), CatalogMigrationCheckpoint.class);
  }

  private void insertProducts() {
    jdbcTemplate.update(
        "insert into categories (id, name, version) values (?, 'Parity', 0)", CATEGORY_ID);
    for (int index = 0; index < PRODUCTS; index++) {
      UUID productId = productId(index);
      jdbcTemplate.update(
          "insert into products (id, name, sku, price, category_id, status, created_at, version) "
              + "values (?, ?, ?, ?, ?, 'ACTIVE', ?, 0)",
          productId,
          name(index),
          sku(index),
          100L + index,
          CATEGORY_ID,
          Timestamp.from(BASE_TIME.plusSeconds(index)));
      insertRelations(index, productId);
    }
  }

  private void insertRelations(int index, UUID productId) {
    jdbcTemplate.update("insert into stock (product_id, available) values (?, 4)", productId);
    jdbcTemplate.update(
        "insert into product_variants "
            + "(id, product_id, sku_suffix, color, size, additional_price) "
            + "values (?, ?, '-M', 'Blue', 'M', 5)",
        productId(index + PRODUCTS),
        productId);
    jdbcTemplate.update(
        "insert into product_attributes (product_id, name, value) values (?, 'material', 'linen')",
        productId);
    insertReview(productId(index * 2 + 2), productId, index, 3);
    insertReview(productId(index * 2 + 3), productId, index, 5);
  }

  private void insertReview(UUID id, UUID productId, int index, int rating) {
    jdbcTemplate.update(
        "insert into reviews (id, product_id, author, rating, comment, created_at) "
            + "values (?, ?, 'Ana', ?, ?, ?)",
        id,
        productId,
        rating,
        "Review " + rating,
        Timestamp.from(BASE_TIME.plusSeconds(index + rating)));
  }

  private String name(int index) {
    return switch (index) {
      case 0 -> "apple";
      case 1 -> "Banana";
      case 2 -> "Éclair";
      case 3 -> "a.b product";
      case 4 -> "Parenthesis ( product";
      default -> "Product " + index;
    };
  }

  private UUID productId(int index) {
    return UUID.nameUUIDFromBytes(
        ("parity-product-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private String sku(int index) {
    return "PARITY-%03d".formatted(index);
  }
}
