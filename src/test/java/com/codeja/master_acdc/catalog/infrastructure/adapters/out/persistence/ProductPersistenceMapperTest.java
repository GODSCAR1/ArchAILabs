package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchRow;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductPersistenceMapperTest {

  private static final UUID PRODUCT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

  @Test
  void should_map_product_row_when_stock_is_available() {
    ProductJpaEntity product = product(mock(CatalogStockJpaEntity.class));
    given(product.stock().available()).willReturn(7);

    ProductSearchRow row = ProductPersistenceMapper.toRow(product);

    assertThat(row)
        .isEqualTo(
            new ProductSearchRow(PRODUCT_ID, "Laptop", 200, 7, "Office", ProductStatus.ACTIVE));
  }

  @Test
  void should_map_zero_stock_when_stock_is_absent() {
    ProductJpaEntity product = product(null);

    ProductSearchRow row = ProductPersistenceMapper.toRow(product);

    assertThat(row)
        .isEqualTo(
            new ProductSearchRow(PRODUCT_ID, "Laptop", 200, 0, "Office", ProductStatus.ACTIVE));
  }

  private ProductJpaEntity product(CatalogStockJpaEntity stock) {
    ProductJpaEntity product = mock(ProductJpaEntity.class);
    CategoryJpaEntity category = mock(CategoryJpaEntity.class);
    given(product.id()).willReturn(PRODUCT_ID);
    given(product.name()).willReturn("Laptop");
    given(product.price()).willReturn(200L);
    given(product.status()).willReturn("ACTIVE");
    given(product.category()).willReturn(category);
    given(category.name()).willReturn("Office");
    given(product.stock()).willReturn(stock);
    return product;
  }
}
