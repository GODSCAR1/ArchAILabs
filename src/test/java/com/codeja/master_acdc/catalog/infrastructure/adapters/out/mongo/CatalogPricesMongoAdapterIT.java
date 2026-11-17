package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.catalog.api.CatalogPrices.ProductPrice;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

class CatalogPricesMongoAdapterIT extends IntegrationTest {
  @Autowired private MongoTemplate mongo;

  @Test
  @DisplayName("Lectura por IDs devuelve los precios actuales de MongoDB")
  void should_return_current_prices_when_product_ids_exist() {
    UUID id = UUID.randomUUID();
    mongo.insert(
        new CatalogProduct(
            id,
            "SKU-PRICE",
            "Producto",
            250,
            ProductStatus.ACTIVE,
            Instant.parse("2026-01-01T00:00:00Z"),
            0,
            new CategoryRef(UUID.randomUUID(), "Categoría"),
            List.of(),
            Map.of(),
            0,
            new RatingSummary(BigDecimal.ZERO, 0),
            List.of()));

    assertThat(new CatalogPricesMongoAdapter(mongo).findByIds(List.of(id, UUID.randomUUID())))
        .containsExactly(new ProductPrice(id, "SKU-PRICE", 250));
  }
}
