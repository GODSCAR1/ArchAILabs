package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchRow;

public final class CatalogProductSearchMongoMapper {

  private CatalogProductSearchMongoMapper() {}

  public static ProductSearchRow toRow(CatalogProductSearchView product) {
    return new ProductSearchRow(
        product.id(),
        product.name(),
        product.price(),
        product.availableStock(),
        product.category().name(),
        product.status());
  }
}
