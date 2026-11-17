package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.IntegrationTest;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class CatalogContractMigrationIT extends IntegrationTest {

  private static final String[] CATALOG_TABLES = {
    "reviews", "product_attributes", "product_variants", "products", "categories"
  };

  @Autowired private PostgreSQLContainer<?> postgres;
  @Autowired private JdbcTemplate admin;

  @BeforeEach
  void create_database() {
    admin.execute("DROP DATABASE IF EXISTS catalog_contract_migration WITH (FORCE)");
    admin.execute("CREATE DATABASE catalog_contract_migration");
  }

  @AfterEach
  void drop_database() {
    admin.execute("DROP DATABASE IF EXISTS catalog_contract_migration WITH (FORCE)");
  }

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
    source.setUrl(
        "jdbc:postgresql://"
            + postgres.getHost()
            + ":"
            + postgres.getMappedPort(5432)
            + "/catalog_contract_migration");
    source.setUsername(postgres.getUsername());
    source.setPassword(postgres.getPassword());
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
