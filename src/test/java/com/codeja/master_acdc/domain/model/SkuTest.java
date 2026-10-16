package com.codeja.master_acdc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.domain.exception.InvalidProductException;
import org.junit.jupiter.api.Test;

class SkuTest {

  @Test
  void should_normalize_to_trimmed_uppercase_when_valid_sku() {
    assertThat(new Sku(" caf-500 ").value()).isEqualTo("CAF-500");
  }

  @Test
  void should_accept_when_sku_has_three_characters() {
    assertThat(new Sku("ABC").value()).isEqualTo("ABC");
  }

  @Test
  void should_reject_when_sku_has_two_characters() {
    assertThatThrownBy(() -> new Sku("AB")).isInstanceOf(InvalidProductException.class);
  }

  @Test
  void should_accept_when_sku_has_sixty_four_characters() {
    String sku = "A".repeat(64);

    assertThat(new Sku(sku).value()).isEqualTo(sku);
  }

  @Test
  void should_reject_when_sku_has_sixty_five_characters() {
    assertThatThrownBy(() -> new Sku("A".repeat(65))).isInstanceOf(InvalidProductException.class);
  }

  @Test
  void should_reject_when_sku_contains_underscore() {
    assertThatThrownBy(() -> new Sku("CAF_500")).isInstanceOf(InvalidProductException.class);
  }

  @Test
  void should_reject_when_sku_is_null() {
    assertThatThrownBy(() -> new Sku(null)).isInstanceOf(InvalidProductException.class);
  }
}
