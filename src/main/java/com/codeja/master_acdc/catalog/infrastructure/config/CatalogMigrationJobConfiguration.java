package com.codeja.master_acdc.catalog.infrastructure.config;

import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogMigrationJob;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProductMigrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class CatalogMigrationJobConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "catalog.migration", name = "enabled", havingValue = "true")
  public CatalogMigrationJob catalogMigrationJob(
      CatalogProductMigrationService migrationService,
      @Value("${catalog.migration.batch-size:100}") int batchSize) {
    return new CatalogMigrationJob(migrationService, batchSize, true);
  }
}
