package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.model.Category;
import com.codeja.master_acdc.catalog.domain.model.Price;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.ProductName;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.domain.model.Sku;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ProductMongoMapper {

  private ProductMongoMapper() {}

  static CatalogProduct toDocument(Product product) {
    return new CatalogProduct(
        product.id(),
        product.sku().value(),
        product.name().value(),
        product.price().value(),
        product.status(),
        product.createdAt(),
        product.version(),
        new CategoryRef(product.categoryId(), "Sin categoría"),
        List.of(),
        Map.of(),
        0,
        new RatingSummary(java.math.BigDecimal.ZERO, 0),
        List.of());
  }

  static Product toDomain(CatalogProduct product) {
    Category category = new Category(product.category().id(), product.category().name());
    return new Product(
        product.id(),
        new ProductName(product.name()),
        new Sku(product.sku()),
        new Price(product.price()),
        category.id(),
        ProductStatus.valueOf(product.status().name()),
        product.createdAt(),
        product.version());
  }

  static CatalogProduct edited(CatalogProduct product, Product edited) {
    return new CatalogProduct(
        product.id(),
        product.sku(),
        edited.name().value(),
        edited.price().value(),
        product.status(),
        product.createdAt(),
        edited.version() + 1,
        product.category(),
        product.variants(),
        new TreeMap<>(product.attributes()),
        product.availableStock(),
        product.rating(),
        product.latestReviews());
  }
}
