package com.codeja.master_acdc.catalog.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogStockProjectionJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CatalogSchedulingConfigurationTest {

  @Test
  @DisplayName("El job no se registra cuando la sincronización está deshabilitada")
  void should_not_register_stock_job_when_stock_sync_is_disabled() {
    new ApplicationContextRunner()
        .withUserConfiguration(CatalogSchedulingConfiguration.class)
        .withPropertyValues("catalog.stock-sync.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(CatalogStockProjectionJob.class));
  }

  @Test
  @DisplayName("El job permite habilitación por defecto")
  void should_enable_stock_sync_when_property_is_missing_by_default() throws Exception {
    ConditionalOnProperty condition =
        CatalogSchedulingConfiguration.class
            .getMethod(
                "catalogStockProjectionJob",
                org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class,
                org.springframework.data.mongodb.core.MongoTemplate.class,
                com.codeja.master_acdc.catalog.domain.port.ProductChangedPublisher.class)
            .getAnnotation(ConditionalOnProperty.class);

    assertThat(condition).isNotNull();
    assertThat(condition.matchIfMissing()).isTrue();
  }
}
