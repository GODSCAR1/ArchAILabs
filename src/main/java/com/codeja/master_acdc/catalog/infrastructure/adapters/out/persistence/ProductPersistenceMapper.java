package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.catalog.domain.model.Price;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductName;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchRow;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.Sku;

final class ProductPersistenceMapper {

  private ProductPersistenceMapper() {}

  static ProductJpaEntity toEntity(Product product) {
    return new ProductJpaEntity(
        new ProductJpaData(
            product.id(),
            product.name().value(),
            product.sku().value(),
            product.price().value(),
            product.categoryId(),
            product.status().name(),
            product.createdAt(),
            product.version()));
  }

  static Product toDomain(ProductJpaEntity product) {
    return new Product(
        product.id(),
        new ProductName(product.name()),
        new Sku(product.sku()),
        new Price(product.price()),
        product.categoryId(),
        ProductStatus.valueOf(product.status()),
        product.createdAt(),
        product.version());
  }

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
