package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductReview;
import com.codeja.master_acdc.catalog.domain.model.ProductVariant;
import java.time.Instant;

public final class CatalogProductMongoMapper {

  private static final int MAX_LATEST_REVIEWS = 5;

  private CatalogProductMongoMapper() {}

  public static CatalogProduct toDocument(ProductDetail detail, Instant createdAt, long version) {
    return new CatalogProduct(
        detail.id(),
        detail.sku(),
        detail.name(),
        detail.price(),
        detail.status(),
        createdAt,
        version,
        new CategoryRef(detail.category().id(), detail.category().name()),
        detail.variants().stream().map(CatalogProductMongoMapper::toVariant).toList(),
        detail.attributes(),
        detail.stock(),
        new RatingSummary(detail.rating().average(), detail.rating().count()),
        detail.recentReviews().stream()
            .limit(MAX_LATEST_REVIEWS)
            .map(CatalogProductMongoMapper::toSnippet)
            .toList());
  }

  public static ProductDetail toDomain(CatalogProduct document) {
    return new ProductDetail(
        document.id(),
        document.sku(),
        document.name(),
        document.price(),
        document.status(),
        new Category(document.category().id(), document.category().name()),
        document.availableStock(),
        document.variants().stream().map(CatalogProductMongoMapper::toDomain).toList(),
        document.attributes(),
        new ProductRating(document.rating().average(), document.rating().count()),
        document.latestReviews().stream().map(CatalogProductMongoMapper::toDomain).toList());
  }

  private static CatalogVariant toVariant(ProductVariant variant) {
    return new CatalogVariant(
        variant.id(),
        variant.skuSuffix(),
        variant.color(),
        variant.size(),
        variant.additionalPrice());
  }

  private static ProductVariant toDomain(CatalogVariant variant) {
    return new ProductVariant(
        variant.id(),
        variant.skuSuffix(),
        variant.color(),
        variant.size(),
        variant.additionalPrice());
  }

  private static ReviewSnippet toSnippet(ProductReview review) {
    return new ReviewSnippet(
        review.id(), review.author(), review.rating(), review.comment(), review.createdAt());
  }

  private static ProductReview toDomain(ReviewSnippet review) {
    return new ProductReview(
        review.id(), review.author(), review.rating(), review.comment(), review.createdAt());
  }
}
