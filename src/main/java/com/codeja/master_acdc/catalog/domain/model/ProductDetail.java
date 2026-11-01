package com.codeja.master_acdc.catalog.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductDetail(
    UUID id,
    String sku,
    String name,
    long price,
    ProductStatus status,
    Category category,
    int stock,
    List<ProductVariant> variants,
    Map<String, String> attributes,
    ProductRating rating,
    List<ProductReview> recentReviews) {

  public ProductDetail {
    variants = List.copyOf(variants);
    attributes = Map.copyOf(attributes);
    recentReviews = List.copyOf(recentReviews);
    if (stock < 0) {
      throw new IllegalArgumentException("stock cannot be negative");
    }
  }
}
