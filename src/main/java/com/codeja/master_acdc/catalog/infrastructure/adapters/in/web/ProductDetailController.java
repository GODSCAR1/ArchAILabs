package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.application.port.ProductReviewsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductDetailController {

  private final ProductDetailUseCase productDetailUseCase;
  private final ProductReviewsUseCase productReviewsUseCase;

  public ProductDetailController(
      ProductDetailUseCase productDetailUseCase, ProductReviewsUseCase productReviewsUseCase) {
    this.productDetailUseCase = productDetailUseCase;
    this.productReviewsUseCase = productReviewsUseCase;
  }

  @GetMapping("/api/products/{sku}")
  public ProductDetailResponse detail(@PathVariable String sku) {
    return ProductDetailWebMapper.toResponse(productDetailUseCase.findBySku(sku));
  }

  @GetMapping("/api/products/{sku}/reviews")
  public ProductReviewPageResponse reviews(
      @PathVariable String sku,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    ProductWebMapper.validatePagination(page, size);
    return ProductDetailWebMapper.toResponse(productReviewsUseCase.findBySku(sku, page, size));
  }
}
