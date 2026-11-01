package com.codeja.master_acdc.catalog.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class CatalogReadStoreConfigurationTest {

  @Test
  @DisplayName("Infraestructura Mongo activada por lectura o migración")
  void should_activate_mongo_infrastructure_when_read_or_migration_is_enabled() throws Exception {
    ConditionalOnExpression activation =
        CatalogMigrationJobConfiguration.class.getAnnotation(ConditionalOnExpression.class);
    assertThat(activation.value())
        .contains("catalog.read-store:postgres")
        .contains("catalog.migration.enabled:false");
    assertThat(condition("mongoProductSearchRepository").havingValue()).isEqualTo("mongo");
    assertThat(condition("productSearchRepository").havingValue()).isEqualTo("postgres");
    assertThat(condition("mongoProductDetailRepository").havingValue()).isEqualTo("mongo");
    assertThat(condition("productDetailRepository").havingValue()).isEqualTo("postgres");
  }

  private ConditionalOnProperty condition(String methodName) throws Exception {
    Method method =
        java.util.Arrays.stream(ProductConfiguration.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
    return method.getAnnotation(ConditionalOnProperty.class);
  }
}
