package com.codeja.master_acdc.catalog.domain.model;

import java.util.List;

public record ProductReviewPage(
    List<ProductReview> content, int page, int size, long totalElements, int totalPages) {

  public ProductReviewPage {
    content = List.copyOf(content);
  }
}
