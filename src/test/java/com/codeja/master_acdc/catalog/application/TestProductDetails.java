package com.codeja.master_acdc.catalog.application;

import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TestProductDetails {

  private TestProductDetails() {}

  static ProductDetail empty() {
    return new ProductDetail(
        UUID.randomUUID(),
        "SKU-EMPTY",
        "Empty",
        100,
        ProductStatus.ACTIVE,
        new Category(UUID.randomUUID(), "Category"),
        0,
        List.of(),
        Map.of(),
        new ProductRating(BigDecimal.ZERO.setScale(2), 0),
        List.of());
  }
}
