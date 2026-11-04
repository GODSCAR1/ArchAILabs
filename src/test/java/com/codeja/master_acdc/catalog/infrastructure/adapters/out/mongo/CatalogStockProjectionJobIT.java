package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.event.ProductChanged;
import com.codeja.master_acdc.catalog.domain.port.ProductChangedPublisher;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CatalogStockProjectionJobIT {

  private static final List<UUID> PRODUCT_IDS =
      List.of(
          UUID.fromString("50000000-0000-0000-0000-000000000001"),
          UUID.fromString("50000000-0000-0000-0000-000000000002"),
          UUID.fromString("50000000-0000-0000-0000-000000000003"));

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static MongoClient client;
  private static MongoTemplate mongoTemplate;
  private static DriverManagerDataSource dataSource;
  private CountingJdbcTemplate jdbcTemplate;
  private ProductChangedPublisher publisher;

  @BeforeAll
  static void initialize_databases() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build());
    mongoTemplate = new MongoTemplate(client, "catalog_stock_projection_test");
    dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    new NamedParameterJdbcTemplate(dataSource)
        .getJdbcTemplate()
        .execute("create table stock (product_id uuid primary key, available integer not null)");
  }

  @AfterAll
  static void close_mongo_client() {
    client.close();
  }

  @BeforeEach
  void seed_test_data() {
    mongoTemplate.getDb().drop();
    var jdbc = new NamedParameterJdbcTemplate(dataSource).getJdbcTemplate();
    jdbc.execute("truncate table stock");
    jdbc.update("insert into stock values (?, ?)", PRODUCT_IDS.get(0), 7);
    jdbc.update("insert into stock values (?, ?)", PRODUCT_IDS.get(2), 4);
    mongoTemplate.insertAll(PRODUCT_IDS.stream().map(this::product).toList());
    jdbcTemplate = new CountingJdbcTemplate(dataSource);
    publisher = org.mockito.Mockito.mock(ProductChangedPublisher.class);
  }

  @Test
  @DisplayName("Actualización periódica de stock")
  void should_update_stock_when_products_have_stock_and_missing_rows() {
    new CatalogStockProjectionJob(jdbcTemplate, mongoTemplate, 2, publisher).synchronizeStock();

    assertThat(stock(PRODUCT_IDS.get(0))).isEqualTo(7);
    assertThat(stock(PRODUCT_IDS.get(1))).isZero();
    assertThat(stock(PRODUCT_IDS.get(2))).isEqualTo(4);
    assertThat(jdbcTemplate.queryCount).isEqualTo(2);
  }

  @Test
  @DisplayName("Cambio de stock en la proyección")
  void should_invalidate_only_skus_when_available_stock_changed() {
    new CatalogStockProjectionJob(jdbcTemplate, mongoTemplate, 2, publisher).synchronizeStock();

    BDDMockito.then(publisher).should().publish(new ProductChanged("SKU-STOCK-0"));
    BDDMockito.then(publisher).should().publish(new ProductChanged("SKU-STOCK-2"));
    BDDMockito.then(publisher).shouldHaveNoMoreInteractions();
  }

  @Test
  @DisplayName("Producto sin fila de stock")
  void should_assign_zero_when_stock_rows_are_absent() {
    new NamedParameterJdbcTemplate(dataSource).getJdbcTemplate().execute("truncate table stock");

    new CatalogStockProjectionJob(jdbcTemplate, mongoTemplate, 2, publisher).synchronizeStock();

    assertThat(PRODUCT_IDS).allSatisfy(id -> assertThat(stock(id)).isZero());
    BDDMockito.then(publisher).shouldHaveNoInteractions();
  }

  private int stock(UUID id) {
    return mongoTemplate
        .findOne(Query.query(Criteria.where("_id").is(id)), CatalogProduct.class)
        .availableStock();
  }

  private CatalogProduct product(UUID id) {
    int index = PRODUCT_IDS.indexOf(id);
    return new CatalogProduct(
        id,
        "SKU-STOCK-" + index,
        "Product " + index,
        100,
        com.codeja.master_acdc.catalog.domain.model.ProductStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"),
        0,
        new CategoryRef(UUID.randomUUID(), "Category"),
        List.of(),
        Map.of(),
        0,
        new RatingSummary(BigDecimal.ZERO.setScale(2), 0),
        List.of());
  }

  private static final class CountingJdbcTemplate extends NamedParameterJdbcTemplate {

    private int queryCount;

    private CountingJdbcTemplate(DriverManagerDataSource source) {
      super(source);
    }

    @Override
    public <T> T query(String sql, Map<String, ?> parameters, ResultSetExtractor<T> extractor) {
      queryCount++;
      return super.query(sql, parameters, extractor);
    }
  }
}
