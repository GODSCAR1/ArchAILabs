package com.codeja.master_acdc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductTest {

  @Test
  void should_generate_id_when_creating_product() {
    Product product =
        Product.create(
            new ProductName("Cafe"), new Sku("CAF-500"), Price.of(new BigDecimal("100")));

    assertThat(product.id()).isNotNull();
    assertThat(product.name().value()).isEqualTo("Cafe");
    assertThat(product.sku().value()).isEqualTo("CAF-500");
    assertThat(product.price().value()).isEqualTo(100L);
  }
}
