package com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class InventoryRepositoryAdapterIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired StockRepositoryAdapter adapter;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void should_discount_stock_when_update_matches() {
    insertStock(A, 10);

    assertThat(adapter.discountIfAvailable(A, 3)).isTrue();

    assertThat(availableOf(A)).isEqualTo(7);
  }

  @Test
  void should_report_insufficient_when_update_matches_nothing() {
    insertStock(A, 1);

    assertThat(adapter.discountIfAvailable(A, 2)).isFalse();

    assertThat(availableOf(A)).isEqualTo(1);
  }

  private void insertStock(UUID productId, int available) {
    jdbcTemplate.update(
        "insert into stock (product_id, available) values (?, ?)", productId, available);
  }

  private int availableOf(UUID productId) {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?", Integer.class, productId);
  }
}
