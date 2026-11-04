package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import org.springframework.cache.annotation.Cacheable;

/** Adds the product-detail cache outside the transaction-owning delegate. */
public class CachedProductDetailUseCase implements ProductDetailUseCase {

  private final ProductDetailUseCase delegate;

  public CachedProductDetailUseCase(ProductDetailUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @Cacheable(cacheNames = "products", key = "#sku", sync = true)
  public ProductDetail findBySku(String sku) {
    return delegate.findBySku(sku);
  }
}
