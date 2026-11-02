package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.catalog.domain.exception.ConcurrentProductModificationException;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.Price;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductName;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.Sku;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class ProductEditingMongoRepositoryAdapterTest {

  private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
  private final ProductEditingMongoRepositoryAdapter adapter =
      new ProductEditingMongoRepositoryAdapter(mongoTemplate);

  @Test
  @DisplayName("Conflicto detectado al guardar")
  void should_throw_conflict_when_version_changes_before_conditional_update() {
    Product product = product();
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(CatalogProduct.class)))
        .thenReturn(null);
    when(mongoTemplate.exists(any(Query.class), eq(CatalogProduct.class))).thenReturn(true);

    assertThatThrownBy(() -> adapter.save(product))
        .isInstanceOf(ConcurrentProductModificationException.class);
  }

  @Test
  @DisplayName("Producto inexistente")
  void should_throw_not_found_when_conditional_update_has_no_matching_product() {
    when(mongoTemplate.findAndModify(
            any(Query.class),
            any(Update.class),
            any(FindAndModifyOptions.class),
            eq(CatalogProduct.class)))
        .thenReturn(null);
    when(mongoTemplate.exists(any(Query.class), eq(CatalogProduct.class))).thenReturn(false);

    assertThatThrownBy(() -> adapter.save(product())).isInstanceOf(ProductNotFoundException.class);
  }

  private Product product() {
    return new Product(
        UUID.randomUUID(),
        new ProductName("Updated"),
        new Sku("SKU-101"),
        new Price(200),
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        ProductStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"),
        0);
  }
}
