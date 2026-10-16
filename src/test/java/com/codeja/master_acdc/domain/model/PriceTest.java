package com.codeja.master_acdc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.domain.exception.InvalidProductException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceTest {

  @Test
  void should_accept_when_price_is_one() {
    assertThat(Price.of(new BigDecimal("1")).value()).isEqualTo(1L);
  }

  @Test
  void should_reject_when_price_is_zero() {
    assertThatThrownBy(() -> Price.of(new BigDecimal("0")))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void should_reject_when_price_is_negative() {
    assertThatThrownBy(() -> Price.of(new BigDecimal("-5")))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void should_reject_when_price_has_decimals() {
    assertThatThrownBy(() -> Price.of(new BigDecimal("1500.5")))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void should_reject_when_price_is_null() {
    assertThatThrownBy(() -> Price.of(null)).isInstanceOf(InvalidProductException.class);
  }
}
