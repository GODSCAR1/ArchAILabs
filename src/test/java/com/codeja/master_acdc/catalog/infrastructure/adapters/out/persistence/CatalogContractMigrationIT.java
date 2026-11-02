package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CatalogContractMigrationIT {

  private static final String[] CATALOG_TABLES = {
    "reviews", "product_attributes", "product_variants", "products", "categories"
  };

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Test
  @DisplayName("V7 elimina solo las tablas PostgreSQL del catálogo")
  void should_drop_catalog_tables_and_preserve_stock_and_ordering_when_migrated_to_v7() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource());
    migrate(jdbc, "6");
    assertThat(tables(jdbc)).contains(CATALOG_TABLES).contains("stock", "orders", "order_lines");

    migrate(jdbc, "7");

    assertThat(tables(jdbc))
        .doesNotContain(CATALOG_TABLES)
        .contains("stock", "orders", "order_lines");
  }

  private DataSource dataSource() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    return source;
  }

  private void migrate(JdbcTemplate jdbc, String target) {
    Flyway.configure()
        .dataSource(jdbc.getDataSource())
        .locations("classpath:db/migration")
        .target(org.flywaydb.core.api.MigrationVersion.fromVersion(target))
        .load()
        .migrate();
  }

  private java.util.Set<String> tables(JdbcTemplate jdbc) {
    return Set.copyOf(
        jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'",
            String.class));
  }
}
