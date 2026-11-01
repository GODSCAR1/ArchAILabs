package com.codeja.master_acdc.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.codeja.master_acdc.catalog.domain.exception.ConcurrentProductModificationException;
import com.codeja.master_acdc.catalog.domain.exception.InvalidProductException;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.Price;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductName;
import com.codeja.master_acdc.catalog.domain.model.Sku;
import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditProductServiceTest {

  private final ProductEditingRepository productRepository = mock(ProductEditingRepository.class);
  private final EditProductService service = new EditProductService(productRepository);

  @Test
  void should_update_name_and_price_when_version_is_current() {
    Product current = product();
    given(productRepository.findById(current.id())).willReturn(Optional.of(current));
    Product expectedToSave =
        current.edit(new ProductName("Updated"), Price.of(new BigDecimal("250")));
    Product persisted =
        new Product(
            expectedToSave.id(),
            expectedToSave.name(),
            expectedToSave.sku(),
            expectedToSave.price(),
            expectedToSave.categoryId(),
            expectedToSave.status(),
            expectedToSave.createdAt(),
            1L);
    given(productRepository.save(expectedToSave)).willReturn(persisted);

    Product edited = service.edit(current.id(), " Updated ", new BigDecimal("250"), 0L);

    assertThat(edited).isSameAs(persisted);
    assertThat(edited.version()).isEqualTo(1L);
    assertThat(expectedToSave.version()).isEqualTo(0L);
    then(productRepository).should().save(expectedToSave);
  }

  @Test
  void should_reject_when_client_version_is_stale() {
    Product current = product();
    given(productRepository.findById(current.id())).willReturn(Optional.of(current));

    assertThatThrownBy(() -> service.edit(current.id(), "Updated", new BigDecimal("250"), 1L))
        .isInstanceOf(ConcurrentProductModificationException.class);

    then(productRepository).should().findById(current.id());
    then(productRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void should_preserve_product_when_name_or_price_is_invalid() {
    Product current = product();
    given(productRepository.findById(current.id())).willReturn(Optional.of(current));

    assertThatThrownBy(() -> service.edit(current.id(), " ", new BigDecimal("250"), 0L))
        .isInstanceOf(InvalidProductException.class);

    then(productRepository).shouldHaveNoInteractions();
  }

  @Test
  void should_throw_product_not_found_when_id_does_not_exist() {
    UUID productId = UUID.randomUUID();
    given(productRepository.findById(productId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.edit(productId, "Updated", new BigDecimal("250"), 0L))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void should_throw_concurrent_modification_when_version_is_stale() {
    Product current = product();
    given(productRepository.findById(current.id())).willReturn(Optional.of(current));

    assertThatThrownBy(() -> service.edit(current.id(), "Updated", new BigDecimal("250"), 2L))
        .isInstanceOf(ConcurrentProductModificationException.class);
  }

  @Test
  void should_reject_when_version_is_missing() {
    Product current = product();

    assertThatThrownBy(() -> service.edit(current.id(), "Updated", new BigDecimal("250"), null))
        .isInstanceOf(InvalidProductException.class);

    then(productRepository).shouldHaveNoInteractions();
  }

  private static Product product() {
    return Product.create(
        new ProductName("Original"), new Sku("ABC-123"), Price.of(new BigDecimal("100")));
  }
}
