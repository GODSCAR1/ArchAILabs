package com.codeja.master_acdc.catalog.application;

import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductReviewsUseCase;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;
import com.codeja.master_acdc.catalog.domain.port.ProductDetailRepository;

public class ProductDetailService implements ProductDetailUseCase, ProductReviewsUseCase {

  private final ProductDetailRepository repository;

  public ProductDetailService(ProductDetailRepository repository) {
    this.repository = repository;
  }

  @Override
  public ProductDetail findBySku(String sku) {
    return repository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
  }

  @Override
  public ProductReviewPage findBySku(String sku, int page, int size) {
    int normalizedSize = Math.min(size, 100);
    return repository
        .findReviewsBySku(sku, page, normalizedSize)
        .orElseThrow(() -> new ProductNotFoundException(sku));
  }
}
