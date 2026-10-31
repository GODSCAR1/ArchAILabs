package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchRow;
import java.util.UUID;

public record ProductSummaryResponse(
    UUID id, String name, long price, int stock, String categoryName) {

  static ProductSummaryResponse from(ProductSearchRow row) {
    return new ProductSummaryResponse(
        row.id(), row.name(), row.price(), row.stock(), row.categoryName());
  }
}
