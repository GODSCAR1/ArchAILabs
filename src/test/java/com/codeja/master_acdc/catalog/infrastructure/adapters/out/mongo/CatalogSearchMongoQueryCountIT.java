package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.IntegrationTestBeans;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchOrder;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class CatalogSearchMongoQueryCountIT extends IntegrationTest {

  @Autowired private MongoTemplate template;
  private ProductSearchMongoRepositoryAdapter adapter;

  @BeforeEach
  void initialize_products() {
    adapter = new ProductSearchMongoRepositoryAdapter(template);
    template.insertAll(List.of(product("Product A"), product("Product B"), product("Product C")));
  }

  @Test
  @DisplayName("Dos comandos Mongo por búsqueda")
  void should_run_exactly_two_mongo_commands_when_searching_products() {
    IntegrationTestBeans.resetReadCommands();

    var result = adapter.search(criteria());

    assertThat(result.totalElements()).isEqualTo(3);
    assertThat(IntegrationTestBeans.readCommands()).isEqualTo(2);
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
