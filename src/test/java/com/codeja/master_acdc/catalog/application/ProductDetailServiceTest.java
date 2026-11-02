package com.codeja.master_acdc.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;
import com.codeja.master_acdc.catalog.domain.port.ProductDetailRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductDetailServiceTest {

  @Test
  @DisplayName("Producto sin stock ni datos opcionales")
  void should_return_empty_optional_data_when_product_has_no_associations() {
    ProductDetailRepository repository = mock(ProductDetailRepository.class);
    ProductDetail expected = TestProductDetails.empty();
    when(repository.findBySku("SKU-EMPTY")).thenReturn(Optional.of(expected));

    ProductDetail result = new ProductDetailService(repository).findBySku("SKU-EMPTY");

    assertThat(result.stock()).isZero();
    assertThat(result.variants()).isEmpty();
    assertThat(result.attributes()).isEmpty();
    assertThat(result.rating().average()).isEqualByComparingTo("0.00");
    assertThat(result.rating().count()).isZero();
    verify(repository).findBySku("SKU-EMPTY");
  }

  @Test
  void should_normalize_review_page_size_when_size_exceeds_maximum() {
    ProductDetailRepository repository = mock(ProductDetailRepository.class);
    ProductReviewPage expected = new ProductReviewPage(java.util.List.of(), 0, 100, 0, 0);
    when(repository.findReviewsBySku("SKU-CAFE", 0, 100)).thenReturn(Optional.of(expected));

    ProductReviewPage result = new ProductDetailService(repository).findBySku("SKU-CAFE", 0, 1000);

    assertThat(result).isSameAs(expected);
    verify(repository).findReviewsBySku("SKU-CAFE", 0, 100);
  }
}
