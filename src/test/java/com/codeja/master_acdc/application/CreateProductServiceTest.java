package com.codeja.master_acdc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.codeja.master_acdc.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.domain.exception.InvalidProductException;
import com.codeja.master_acdc.domain.model.Product;
import com.codeja.master_acdc.domain.model.Sku;
import com.codeja.master_acdc.domain.port.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CreateProductServiceTest {

  private final ProductRepository productRepository = mock(ProductRepository.class);
  private final CreateProductService service = new CreateProductService(productRepository);

  @Test
  void should_save_normalized_product_when_sku_is_new() {
    given(productRepository.existsBySku(new Sku("CAF-500"))).willReturn(false);

    Product created = service.create(" Cafe ", " caf-500 ", new BigDecimal("1500"));

    assertThat(created.id()).isNotNull();
    assertThat(created.name().value()).isEqualTo("Cafe");
    assertThat(created.sku().value()).isEqualTo("CAF-500");
    assertThat(created.price().value()).isEqualTo(1500L);
    then(productRepository).should().save(created);
  }

  @Test
  void should_throw_duplicate_sku_exception_when_sku_already_exists() {
    given(productRepository.existsBySku(new Sku("CAF-500"))).willReturn(true);

    assertThatThrownBy(() -> service.create("Cafe", "caf-500", new BigDecimal("100")))
        .isInstanceOf(DuplicateSkuException.class);

    then(productRepository).should(never()).save(any());
  }

  @Test
  void should_throw_invalid_product_exception_and_not_touch_repository_when_sku_is_invalid() {
    assertThatThrownBy(() -> service.create("Cafe", "AB", new BigDecimal("100")))
        .isInstanceOf(InvalidProductException.class);

    then(productRepository).shouldHaveNoInteractions();
  }
}
