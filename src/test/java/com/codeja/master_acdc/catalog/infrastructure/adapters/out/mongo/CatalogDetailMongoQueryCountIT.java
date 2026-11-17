package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.IntegrationTestBeans;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class CatalogDetailMongoQueryCountIT extends IntegrationTest {

  private static final UUID PRODUCT_ID = UUID.randomUUID();
  @Autowired private MongoTemplate mongoTemplate;
  private CatalogDetailMongoRepositoryAdapter adapter;

  @BeforeEach
  void reset_mongo_data() {
    adapter = new CatalogDetailMongoRepositoryAdapter(mongoTemplate);
    mongoTemplate.insert(product());
    mongoTemplate.insertAll(reviews());
  }

  @Test
  @DisplayName("Ficha Mongo con una operación")
  void should_load_detail_with_one_command_when_product_has_many_embedded_values() {
    IntegrationTestBeans.resetReadCommands();

    var detail = adapter.findBySku("SKU-QUERY-COUNT").orElseThrow();

    assertThat(IntegrationTestBeans.readCommands()).isEqualTo(1);
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
