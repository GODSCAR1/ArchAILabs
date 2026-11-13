package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
class CatalogDetailMongoQueryCountTest {

  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final AtomicInteger READ_COMMANDS = new AtomicInteger();
  private static final CommandListener COMMAND_LISTENER =
      new CommandListener() {
        @Override
        public void commandStarted(CommandStartedEvent event) {
          if (List.of("find", "aggregate").contains(event.getCommandName())) {
            READ_COMMANDS.incrementAndGet();
          }
        }
      };

  @Container
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

  private static MongoClient client;
  private static MongoTemplate mongoTemplate;
  private CatalogDetailMongoRepositoryAdapter adapter;

  @BeforeAll
  static void initialize_mongo_client() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(MONGO.getReplicaSetUrl()))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .addCommandListener(COMMAND_LISTENER)
                .build());
    mongoTemplate = new MongoTemplate(client, "catalog_detail_query_count_test");
  }

  @AfterAll
  static void close_mongo_client() {
    client.close();
  }

  @BeforeEach
  void reset_mongo_data() {
    mongoTemplate.getDb().drop();
    new CatalogIndexInitializer(mongoTemplate).initialize();
    adapter = new CatalogDetailMongoRepositoryAdapter(mongoTemplate);
    mongoTemplate.insert(product());
    mongoTemplate.insertAll(reviews());
  }

  @Test
  @DisplayName("Ficha Mongo con una operación")
  void should_load_detail_with_one_command_when_product_has_many_embedded_values() {
    READ_COMMANDS.set(0);

    var detail = adapter.findBySku("SKU-QUERY-COUNT").orElseThrow();

    assertThat(READ_COMMANDS).hasValue(1);
    assertThat(detail.variants()).hasSize(60);
    assertThat(detail.attributes()).hasSize(60);
    assertThat(detail.recentReviews()).hasSize(5);
    assertThat(mongoTemplate.getCollection("reviews").countDocuments()).isEqualTo(60);
  }

  @Test
  @DisplayName("Reseñas completas consultadas desde su colección")
  void should_page_full_reviews_separately_when_reviews_are_requested() {
    var page = adapter.findReviewsBySku("SKU-QUERY-COUNT", 0, 60).orElseThrow();

    assertThat(page.content()).hasSize(60);
    assertThat(page.totalElements()).isEqualTo(60);
  }

  private CatalogProduct product() {
    Map<String, String> attributes = new HashMap<>();
    List<CatalogVariant> variants = new ArrayList<>();
    for (int index = 0; index < 60; index++) {
      attributes.put("attribute-" + index, "value-" + index);
      variants.add(new CatalogVariant(UUID.randomUUID(), "-" + index, "Blue", "M", index));
    }
    return new CatalogProduct(
        PRODUCT_ID,
        "SKU-QUERY-COUNT",
        "Product",
        1200,
        com.codeja.master_acdc.catalog.domain.model.ProductStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"),
        0,
        new CategoryRef(UUID.randomUUID(), "Category"),
        variants,
        attributes,
        10,
        new RatingSummary(new BigDecimal("4.50"), 60),
        latestReviews());
  }

  private List<ReviewSnippet> latestReviews() {
    List<ReviewSnippet> snippets = new ArrayList<>();
    for (int index = 0; index < 5; index++) {
      snippets.add(
          new ReviewSnippet(
              UUID.randomUUID(),
              "Reviewer",
              5,
              "Recent comment " + index,
              Instant.parse("2026-01-02T00:00:00Z").plusSeconds(index)));
    }
    return snippets;
  }

  private List<CatalogReview> reviews() {
    List<CatalogReview> reviews = new ArrayList<>();
    for (int index = 0; index < 60; index++) {
      reviews.add(
          new CatalogReview(
              UUID.randomUUID(),
              PRODUCT_ID,
              "Reviewer",
              5,
              "Comment " + index,
              Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index)));
    }
    return reviews;
  }
}
