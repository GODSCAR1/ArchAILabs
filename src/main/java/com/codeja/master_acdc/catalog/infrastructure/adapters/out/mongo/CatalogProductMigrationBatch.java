package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.util.List;
import java.util.UUID;

public record CatalogProductMigrationBatch(
    UUID lastProcessedId,
    boolean complete,
    int migratedProducts,
    int variantsProcessed,
    int reviewsProcessed,
    List<String> rejectedSkus) {

  public CatalogProductMigrationBatch {
    rejectedSkus = List.copyOf(rejectedSkus);
  }
}
