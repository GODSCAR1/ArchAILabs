package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.model.Price;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductName;
import com.codeja.master_acdc.catalog.domain.model.Sku;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ProductEditingRepositoryAdapterIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired ProductRepositoryAdapter productRepositoryAdapter;
  @Autowired ProductEditingRepositoryAdapter editingRepositoryAdapter;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from products");
  }

  @Test
  void should_return_versions_and_preserve_immutable_fields_when_product_is_edited() {
    Product original =
        Product.create(
            new ProductName("Original"), new Sku("ABC-123"), Price.of(new BigDecimal("100")));
    productRepositoryAdapter.save(original);

    Product loaded = editingRepositoryAdapter.findById(original.id()).orElseThrow();
    Map<String, Object> before = row(original.id());
    Product requested = loaded.edit(new ProductName("Updated"), Price.of(new BigDecimal("250")));
    Product persisted = editingRepositoryAdapter.save(requested);
    Map<String, Object> after = row(original.id());

    assertThat(loaded.version()).isZero();
    assertThat(persisted.version()).isEqualTo(1L);
    assertThat(after.get("name")).isEqualTo("Updated");
    assertThat(after.get("price")).isEqualTo(250L);
    assertThat(after.get("sku")).isEqualTo(before.get("sku"));
    assertThat(after.get("category_id")).isEqualTo(before.get("category_id"));
    assertThat(after.get("status")).isEqualTo(before.get("status"));
    assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
  }

  private Map<String, Object> row(java.util.UUID productId) {
    return jdbcTemplate.queryForMap(
        "select name, price, sku, category_id, status, created_at from products where id = ?",
        productId);
  }
}
