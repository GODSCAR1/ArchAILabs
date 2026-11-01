package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogMigrationJobTest {

  @Test
  @DisplayName("Migración habilitada")
  void should_run_full_migration_when_job_is_enabled() {
    CatalogProductMigrationService migrationService = mock(CatalogProductMigrationService.class);
    CatalogMigrationJob job = new CatalogMigrationJob(migrationService, 25, true);

    job.run();

    verify(migrationService).migrateAll(25);
  }

  @Test
  @DisplayName("Migración desactivada")
  void should_skip_migration_when_job_is_disabled() {
    CatalogProductMigrationService migrationService = mock(CatalogProductMigrationService.class);
    CatalogMigrationJob job = new CatalogMigrationJob(migrationService, 25, false);

    job.run();

    verifyNoInteractions(migrationService);
  }
}
