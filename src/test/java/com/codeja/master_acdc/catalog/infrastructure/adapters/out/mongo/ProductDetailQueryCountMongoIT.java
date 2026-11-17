package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.IntegrationTestBeans;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class ProductDetailQueryCountMongoIT extends IntegrationTest {

  @Autowired private MongoTemplate template;
  private CatalogDetailMongoRepositoryAdapter adapter;

  @BeforeEach
  void initialize_products() {
    adapter = new CatalogDetailMongoRepositoryAdapter(template);
    template.insert(product());
  }

  @Test
  @DisplayName("Muchas colecciones asociadas")
  void should_load_product_detail_when_one_find_command_is_issued() {
    IntegrationTestBeans.resetReadCommands();

    var detail = adapter.findBySku("SKU-DETAIL-COUNT").orElseThrow();

    assertThat(detail.sku()).isEqualTo("SKU-DETAIL-COUNT");
    assertThat(IntegrationTestBeans.readCommands()).isEqualTo(1);
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
