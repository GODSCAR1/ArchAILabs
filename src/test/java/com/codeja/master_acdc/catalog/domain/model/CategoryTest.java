package com.codeja.master_acdc.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoryTest {

  @Test
  void should_keep_id_and_name_when_category_is_valid() {
    UUID id = UUID.randomUUID();

    Category category = new Category(id, "Bebidas");

    assertThat(category.id()).isEqualTo(id);
    assertThat(category.name()).isEqualTo("Bebidas");
  }
}
