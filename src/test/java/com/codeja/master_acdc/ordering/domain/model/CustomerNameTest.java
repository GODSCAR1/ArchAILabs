package com.codeja.master_acdc.ordering.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerNameTest {
  @Test
  @DisplayName("La partición conserva caracteres y normaliza solo espacios")
  void should_split_like_migration_when_names_contain_spaces_tabs_and_boundaries() {
    assertThat(CustomerName.split("  Ana  ")).isEqualTo(new CustomerName("Ana", null));
    assertThat(CustomerName.split("  María   del Mar  "))
        .isEqualTo(new CustomerName("María", "del Mar"));
    String first = "Ñ\tO'Connor-";
    String last = "a".repeat(120 - first.length() - 1);
    assertThat(CustomerName.split(first + " " + last)).isEqualTo(new CustomerName(first, last));
    assertThat(CustomerName.split("\tAna\t")).isEqualTo(new CustomerName("\tAna\t", null));
    assertThatThrownBy(() -> CustomerName.split("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
