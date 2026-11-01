package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductReview;
import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;

public final class ProductDetailWebMapper {

  private ProductDetailWebMapper() {}

  public static ProductDetailResponse toResponse(ProductDetail detail) {
    return new ProductDetailResponse(
        detail.id(),
        detail.sku(),
        detail.name(),
        detail.price(),
        detail.status().name(),
        new CategoryResponse(detail.category().id(), detail.category().name()),
        detail.stock(),
        detail.variants().stream()
            .map(
                v ->
                    new ProductVariantResponse(
                        v.id(), v.skuSuffix(), v.color(), v.size(), v.additionalPrice()))
            .toList(),
        detail.attributes(),
        new ProductRatingResponse(detail.rating().average(), detail.rating().count()),
        detail.recentReviews().stream().map(ProductDetailWebMapper::toResponse).toList());
  }

  public static ProductReviewPageResponse toResponse(ProductReviewPage page) {
    return new ProductReviewPageResponse(
        page.content().stream().map(ProductDetailWebMapper::toResponse).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }

  private static ProductReviewResponse toResponse(ProductReview review) {
    return new ProductReviewResponse(
        review.id(), review.author(), review.rating(), review.comment(), review.createdAt());
  }
}
