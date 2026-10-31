package com.codeja.master_acdc.catalog.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Producto del catálogo. El identificador lo genera la aplicación, no la base de datos. */
public record Product(
    UUID id,
    ProductName name,
    Sku sku,
    Price price,
    UUID categoryId,
    ProductStatus status,
    Instant createdAt) {

  public static Product create(ProductName name, Sku sku, Price price) {
    return new Product(
        UUID.randomUUID(),
        name,
        sku,
        price,
        Category.UNCATEGORIZED_ID,
        ProductStatus.ACTIVE,
        Instant.now());
  }
}
