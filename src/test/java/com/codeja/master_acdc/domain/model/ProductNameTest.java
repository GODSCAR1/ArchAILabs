package com.codeja.master_acdc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.domain.exception.InvalidProductException;
import org.junit.jupiter.api.Test;

class ProductNameTest {

  @Test
  void should_trim_when_name_has_surrounding_spaces() {
    assertThat(new ProductName("  Cafe  ").value()).isEqualTo("Cafe");
  }

  @Test
  void should_accept_when_name_has_one_hundred_twenty_characters() {
    String name = "a".repeat(120);

    assertThat(new ProductName(name).value()).isEqualTo(name);
  }

  @Test
  void should_reject_when_name_has_one_hundred_twenty_one_characters() {
    assertThatThrownBy(() -> new ProductName("a".repeat(121)))
        .isInstanceOf(InvalidProductException.class);
  }

  @Test
  void should_reject_when_name_is_blank() {
    assertThatThrownBy(() -> new ProductName("   ")).isInstanceOf(InvalidProductException.class);
  }

  @Test
  void should_reject_when_name_is_null() {
    assertThatThrownBy(() -> new ProductName(null)).isInstanceOf(InvalidProductException.class);
  }
}
