package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchRow;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;

final class ProductPersistenceMapper {

  private ProductPersistenceMapper() {}

  static ProductSearchRow toRow(ProductJpaEntity product) {
    int stock = product.stock() == null ? 0 : product.stock().available();
    return new ProductSearchRow(
        product.id(),
        product.name(),
        product.price(),
        stock,
        product.category().name(),
        ProductStatus.valueOf(product.status()));
  }
}
