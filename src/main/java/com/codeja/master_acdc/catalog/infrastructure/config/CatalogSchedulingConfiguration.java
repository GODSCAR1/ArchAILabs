package com.codeja.master_acdc.catalog.infrastructure.config;

import com.codeja.master_acdc.catalog.domain.port.ProductChangedPublisher;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogStockProjectionJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class CatalogSchedulingConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "catalog.stock-sync.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CatalogStockProjectionJob catalogStockProjectionJob(
      NamedParameterJdbcTemplate jdbcTemplate,
      MongoTemplate mongoTemplate,
      ProductChangedPublisher productChangedPublisher) {
    return new CatalogStockProjectionJob(jdbcTemplate, mongoTemplate, 100, productChangedPublisher);
  }
}
