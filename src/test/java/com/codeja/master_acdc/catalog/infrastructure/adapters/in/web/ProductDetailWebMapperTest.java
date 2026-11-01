package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductRating;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductDetailWebMapperTest {

  @Test
  @DisplayName("Producto sin stock ni datos opcionales")
  void should_map_zero_rating_numerically_when_detail_has_no_optional_data() {
    ProductDetail detail =
        new ProductDetail(
            UUID.randomUUID(),
            "SKU-EMPTY",
            "Empty",
            0,
            ProductStatus.ACTIVE,
            new Category(UUID.randomUUID(), "Category"),
            0,
            List.of(),
            Map.of(),
            ProductRating.empty(),
            List.of());

    ProductDetailResponse response = ProductDetailWebMapper.toResponse(detail);

    assertThat(response.rating().average()).isEqualByComparingTo(new BigDecimal("0"));
    assertThat(response.rating().count()).isZero();
    assertThat(response.variants()).isEmpty();
    assertThat(response.attributes()).isEmpty();
  }
}
