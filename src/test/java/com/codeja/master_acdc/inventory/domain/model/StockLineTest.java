package com.codeja.master_acdc.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.inventory.domain.exception.InvalidStockException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockLineTest {

  @Test
  void should_keep_values_when_line_is_valid() {
    UUID productId = UUID.randomUUID();

    StockLine line = new StockLine(productId, 3);

    assertThat(line.productId()).isEqualTo(productId);
    assertThat(line.quantity()).isEqualTo(3);
  }

  @Test
  void should_reject_quantity_when_not_positive() {
    assertThatThrownBy(() -> new StockLine(UUID.randomUUID(), 0))
        .isInstanceOf(InvalidStockException.class);
    assertThatThrownBy(() -> new StockLine(UUID.randomUUID(), -1))
        .isInstanceOf(InvalidStockException.class);
  }

  @Test
  void should_reject_product_id_when_null() {
    assertThatThrownBy(() -> new StockLine(null, 1)).isInstanceOf(InvalidStockException.class);
  }
}
