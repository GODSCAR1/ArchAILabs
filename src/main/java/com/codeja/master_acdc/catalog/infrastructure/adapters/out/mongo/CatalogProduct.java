package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("catalog_products")
public record CatalogProduct(
    @Id UUID id,
    String sku,
    String name,
    long price,
    ProductStatus status,
    Instant createdAt,
    long version,
    CategoryRef category,
    List<CatalogVariant> variants,
    Map<String, String> attributes,
    int availableStock,
    RatingSummary rating,
    List<ReviewSnippet> latestReviews) {

  public CatalogProduct {
    variants = List.copyOf(variants);
    attributes = Map.copyOf(attributes);
    latestReviews = List.copyOf(latestReviews);
  }
}
