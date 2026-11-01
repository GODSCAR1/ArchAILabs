package com.codeja.master_acdc.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.inventory.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.inventory.domain.exception.InvalidStockException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockBalanceTest {

  private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void should_reserve_when_units_are_positive() {
    StockBalance balance = new StockBalance(PRODUCT_ID, 10);

    StockBalance remaining = balance.reserve(3);

    assertThat(remaining).isEqualTo(new StockBalance(PRODUCT_ID, 7));
    assertThat(balance.available()).isEqualTo(10);
  }

  @Test
  void should_reject_when_available_is_insufficient() {
    StockBalance balance = new StockBalance(PRODUCT_ID, 2);

    assertThatThrownBy(() -> balance.reserve(3))
        .isInstanceOfSatisfying(
            InsufficientStockException.class,
            exception -> assertThat(exception.available()).isEqualTo(2));
  }

  @Test
  void should_reject_when_units_are_non_positive() {
    StockBalance balance = new StockBalance(PRODUCT_ID, 2);

    assertThatThrownBy(() -> balance.reserve(0)).isInstanceOf(InvalidStockException.class);
    assertThatThrownBy(() -> balance.reserve(-1)).isInstanceOf(InvalidStockException.class);
  }
}
