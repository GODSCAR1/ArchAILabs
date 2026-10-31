package com.codeja.master_acdc.catalog.domain.model;

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
    assertThat(product.categoryId()).isEqualTo(Category.UNCATEGORIZED_ID);
    assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    assertThat(product.createdAt()).isNotNull();
  }
}
