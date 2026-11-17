package com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.inventory.domain.exception.StockRecordNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class InventoryRepositoryAdapterIT extends IntegrationTest {

  private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");

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

  @Test
  @DisplayName("Liberación de stock de varias líneas")
  void should_increase_available_stock_when_release_multiple_lines() {
    insertStock(A, 7);
    insertStock(B, 3);

    adapter.release(A, 3);
    adapter.release(B, 2);

    assertThat(availableOf(A)).isEqualTo(10);
    assertThat(availableOf(B)).isEqualTo(5);
  }

  @Test
  void should_throw_when_release_affects_no_row() {
    assertThatThrownBy(() -> adapter.release(A, 3))
        .isInstanceOf(StockRecordNotFoundException.class);
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
