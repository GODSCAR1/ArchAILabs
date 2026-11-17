package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.IntegrationTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class ProductDetailMigrationIT extends IntegrationTest {

  @Autowired private PostgreSQLContainer<?> postgres;
  @Autowired private JdbcTemplate admin;

  @BeforeEach
  void create_database() {
    admin.execute("DROP DATABASE IF EXISTS catalog_detail_migration WITH (FORCE)");
    admin.execute("CREATE DATABASE catalog_detail_migration");
  }

  @AfterEach
  void drop_database() {
    admin.execute("DROP DATABASE IF EXISTS catalog_detail_migration WITH (FORCE)");
  }

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
    dataSource.setUrl(
        "jdbc:postgresql://"
            + postgres.getHost()
            + ":"
            + postgres.getMappedPort(5432)
            + "/catalog_detail_migration");
    dataSource.setUsername(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
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
