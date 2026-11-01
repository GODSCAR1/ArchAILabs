package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.util.List;
import java.util.UUID;

public record CatalogMigrationReport(
    int productsProcessed,
    int variantsProcessed,
    int reviewsProcessed,
    List<String> rejectedSkus,
    UUID lastProcessedId,
    MigrationState state) {

  public CatalogMigrationReport {
    rejectedSkus = List.copyOf(rejectedSkus);
  }
}
