package com.codeja.master_acdc.catalog.infrastructure.config;

import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogIndexInitializer;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogMigrationJob;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProductMigrationService;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogStockProjectionJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnExpression(
    "'${catalog.store:mongo}' == 'mongo' or '${catalog.migration.enabled:false}' ==" + " 'true'")
public class CatalogMigrationJobConfiguration {

  @Bean
  public CatalogProductMigrationService catalogProductMigrationService(
      NamedParameterJdbcTemplate jdbcTemplate, MongoTemplate mongoTemplate) {
    return new CatalogProductMigrationService(jdbcTemplate, mongoTemplate);
  }

  @Bean(initMethod = "initialize")
  public CatalogIndexInitializer catalogIndexInitializer(MongoTemplate mongoTemplate) {
    return new CatalogIndexInitializer(mongoTemplate);
  }

  @Bean
  public CatalogMigrationJob catalogMigrationJob(
      CatalogProductMigrationService migrationService,
      @Value("${catalog.migration.batch-size:100}") int batchSize,
      @Value("${catalog.migration.enabled:false}") boolean enabled) {
    return new CatalogMigrationJob(migrationService, batchSize, enabled);
  }

  @Bean
  public CatalogStockProjectionJob catalogStockProjectionJob(
      NamedParameterJdbcTemplate jdbcTemplate,
      MongoTemplate mongoTemplate,
      @Value("${catalog.migration.batch-size:100}") int batchSize) {
    return new CatalogStockProjectionJob(jdbcTemplate, mongoTemplate, batchSize);
  }
}
