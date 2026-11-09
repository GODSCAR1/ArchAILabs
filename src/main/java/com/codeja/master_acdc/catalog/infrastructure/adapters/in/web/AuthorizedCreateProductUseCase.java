package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.domain.model.Product;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedCreateProductUseCase implements CreateProductUseCase {
  private final CreateProductUseCase delegate;

  public AuthorizedCreateProductUseCase(CreateProductUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @PreAuthorize("hasAuthority('product:write')")
  public Product create(String name, String sku, BigDecimal price) {
    return delegate.create(name, sku, price);
  }
}
