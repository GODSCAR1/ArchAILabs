package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
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
import java.util.Set;
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
class CatalogSearchMongoQueryCountTest {

  private static final AtomicInteger READ_COMMANDS = new AtomicInteger();
  private static final CommandListener COMMAND_LISTENER =
      new CommandListener() {
        @Override
        public void commandStarted(CommandStartedEvent event) {
          if (List.of("find", "aggregate", "count").contains(event.getCommandName())) {
            READ_COMMANDS.incrementAndGet();
          }
        }
      };

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  private static MongoClient client;
  private static MongoTemplate template;
  private ProductSearchMongoRepositoryAdapter adapter;

  @BeforeAll
  static void initialize_mongo_client() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .addCommandListener(COMMAND_LISTENER)
                .build());
    template = new MongoTemplate(client, "catalog_search_query_count_test");
  }

  @AfterAll
  static void close_mongo_client() {
    client.close();
  }

  @BeforeEach
  void initialize_products() {
    template.getDb().drop();
    adapter = new ProductSearchMongoRepositoryAdapter(template);
    template.insertAll(List.of(product("Product A"), product("Product B"), product("Product C")));
  }

  @Test
  @DisplayName("Dos comandos Mongo por búsqueda")
  void should_run_exactly_two_mongo_commands_when_searching_products() {
    READ_COMMANDS.set(0);

    var result = adapter.search(criteria());

    assertThat(result.totalElements()).isEqualTo(3);
    assertThat(READ_COMMANDS).hasValue(2);
  }

  private ProductSearchCriteria criteria() {
    return new ProductSearchCriteria(
        null,
        null,
        null,
        null,
        Set.of(),
        false,
        0,
        20,
        new ProductSearchOrder("name", ProductSearchOrder.Direction.ASC));
  }

  private CatalogProduct product(String name) {
    return new CatalogProduct(
        UUID.randomUUID(),
        "SKU-" + name,
        name,
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
