package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProductSearchMigrationIT {

  private static final UUID PRODUCT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Test
  void should_preserve_product_data_when_schema_is_migrated_from_v4_to_v5() {
    DataSource dataSource = dataSource();
    flyway(dataSource, "4").migrate();
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    insertLegacyProduct(jdbcTemplate);

    flyway(dataSource, "5").migrate();

    ProductRow product = readProduct(jdbcTemplate);
    assertMigratedProduct(jdbcTemplate, product);
  }

  private void insertLegacyProduct(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.update(
        "insert into products (id, name, sku, price) values (?, ?, ?, ?)",
        PRODUCT_ID,
        "Legacy product",
        "LEGACY-1",
        725L);
  }

  private ProductRow readProduct(JdbcTemplate jdbcTemplate) {
    return jdbcTemplate.queryForObject(
        "select id, name, sku, price, category_id, status, version from products where id = ?",
        (resultSet, rowNumber) ->
            new ProductRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("sku"),
                resultSet.getLong("price"),
                resultSet.getObject("category_id", UUID.class),
                resultSet.getString("status"),
                resultSet.getLong("version")),
        PRODUCT_ID);
  }

  private void assertMigratedProduct(JdbcTemplate jdbcTemplate, ProductRow product) {
    assertThat(product)
        .isEqualTo(
            new ProductRow(
                PRODUCT_ID,
                "Legacy product",
                "LEGACY-1",
                725L,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "ACTIVE",
                0));
    assertThat(
            jdbcTemplate.queryForObject(
                "select name from categories where id = ?", String.class, product.categoryId()))
        .isEqualTo("Sin categoría");
    assertThat(
            jdbcTemplate.queryForObject(
                "select created_at from products where id = ?", Object.class, PRODUCT_ID))
        .isNotNull();
  }

  private DataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    return dataSource;
  }

  private Flyway flyway(DataSource dataSource, String target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(target)
        .load();
  }

  private record ProductRow(
      UUID id, String name, String sku, long price, UUID categoryId, String status, long version) {}
}
