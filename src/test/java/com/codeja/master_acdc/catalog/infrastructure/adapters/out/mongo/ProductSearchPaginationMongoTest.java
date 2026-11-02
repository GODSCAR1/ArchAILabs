package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ProductSearchPaginationMongoTest {

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  private static MongoClient client;
  private static MongoTemplate template;
  private ProductSearchMongoRepositoryAdapter adapter;
  private List<CatalogProduct> products;

  @BeforeAll
  static void initialize_mongo_template() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build());
    template = new MongoTemplate(client, "product_search_pagination_mongo_test");
  }

  @AfterAll
  static void close_mongo_client() {
    client.close();
  }

  @BeforeEach
  void insert_products() {
    template.getDb().drop();
    adapter = new ProductSearchMongoRepositoryAdapter(template);
    products = java.util.stream.IntStream.range(1, 61).mapToObj(this::product).toList();
    template.insertAll(products);
  }

  @Test
  @DisplayName("Orden estable y desempate por id en más de cincuenta productos")
  void should_return_stable_pages_when_more_than_fifty_products_have_the_same_sort_value() {
    var first = adapter.search(criteria(0));
    var second = adapter.search(criteria(1));

    assertThat(first.totalElements()).isEqualTo(60);
    assertThat(first.content()).hasSize(50);
    assertThat(second.content()).hasSize(10);
    assertThat(first.content().getFirst().id()).isEqualTo(products.getFirst().id());
    assertThat(second.content().getFirst().id()).isEqualTo(products.get(50).id());
  }

  private ProductSearchCriteria criteria(int page) {
    return new ProductSearchCriteria(
        null,
        null,
        null,
        null,
        Set.of(),
        false,
        page,
        50,
        new ProductSearchOrder("name", ProductSearchOrder.Direction.ASC));
  }

  private CatalogProduct product(int index) {
    return new CatalogProduct(
        UUID.fromString("40000000-0000-0000-0000-%012d".formatted(index)),
        "SKU-" + index,
        "Same name",
        100,
        ProductStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"),
        0,
        new CategoryRef(UUID.randomUUID(), "Category"),
        List.of(),
        Map.of(),
        0,
        new RatingSummary(BigDecimal.ZERO.setScale(2), 0),
        List.of());
  }
}
