package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import org.springframework.scheduling.annotation.Scheduled;

public final class CatalogMigrationJob {

  private final CatalogProductMigrationService migrationService;
  private final int batchSize;
  private final boolean enabled;

  public CatalogMigrationJob(
      CatalogProductMigrationService migrationService, int batchSize, boolean enabled) {
    this.migrationService = migrationService;
    this.batchSize = batchSize;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelayString = "${catalog.migration.interval:PT5M}")
  public void run() {
    if (enabled) {
      migrationService.migrateAll(batchSize);
    }
  }
}
