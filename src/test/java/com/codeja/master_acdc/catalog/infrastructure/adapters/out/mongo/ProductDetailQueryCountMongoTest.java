package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
class ProductDetailQueryCountMongoTest {

  private static final AtomicInteger FIND_COMMANDS = new AtomicInteger();
  private static final CommandListener COMMAND_LISTENER =
      new CommandListener() {
        @Override
        public void commandStarted(CommandStartedEvent event) {
          if ("find".equals(event.getCommandName())) {
            FIND_COMMANDS.incrementAndGet();
          }
        }
      };

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  private static MongoClient client;
  private static MongoTemplate template;
  private CatalogDetailMongoRepositoryAdapter adapter;

  @BeforeAll
  static void initialize_mongo_template() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .addCommandListener(COMMAND_LISTENER)
                .build());
    template = new MongoTemplate(client, "product_detail_query_count_mongo_test");
  }

  @AfterAll
  static void close_mongo_client() {
    client.close();
  }

  @BeforeEach
  void initialize_products() {
    template.getDb().drop();
    adapter = new CatalogDetailMongoRepositoryAdapter(template);
    template.insert(product());
  }

  @Test
  @DisplayName("Una operación Mongo por ficha")
  void should_load_product_detail_when_one_find_command_is_issued() {
    FIND_COMMANDS.set(0);

    var detail = adapter.findBySku("SKU-DETAIL-COUNT").orElseThrow();

    assertThat(detail.sku()).isEqualTo("SKU-DETAIL-COUNT");
    assertThat(FIND_COMMANDS).hasValue(1);
  }

  private CatalogProduct product() {
    return new CatalogProduct(
        UUID.randomUUID(),
        "SKU-DETAIL-COUNT",
        "Product",
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
