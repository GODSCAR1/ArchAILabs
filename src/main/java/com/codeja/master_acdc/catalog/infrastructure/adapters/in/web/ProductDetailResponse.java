package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductDetailResponse(
    UUID id,
    String sku,
    String name,
    long price,
    String status,
    CategoryResponse category,
    int stock,
    List<ProductVariantResponse> variants,
    Map<String, String> attributes,
    ProductRatingResponse rating,
    List<ProductReviewResponse> recentReviews) {}
