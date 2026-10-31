package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import java.util.List;

public record ProductSearchResponse(
    List<ProductSummaryResponse> content, int page, int size, long totalElements, int totalPages) {

  static ProductSearchResponse from(ProductSearchPage result) {
    return new ProductSearchResponse(
        result.content().stream().map(ProductSummaryResponse::from).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages());
  }
}
