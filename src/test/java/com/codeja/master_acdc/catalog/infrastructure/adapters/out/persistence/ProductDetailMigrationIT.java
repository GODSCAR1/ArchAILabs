package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProductDetailMigrationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Test
  void should_create_product_detail_schema_without_seed_rows_when_migration_is_applied() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
    flyway(jdbcTemplate.getDataSource()).migrate();

    assertThat(jdbcTemplate.queryForObject("select count(*) from product_variants", Long.class))
        .isZero();
    assertThat(jdbcTemplate.queryForObject("select count(*) from product_attributes", Long.class))
        .isZero();
    assertThat(jdbcTemplate.queryForObject("select count(*) from reviews", Long.class)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint where conname = ?",
                Long.class,
                "fk_reviews_product"))
        .isEqualTo(1);
  }

  private DataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    return dataSource;
  }

  private Flyway flyway(DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target("6")
        .load();
  }
}
