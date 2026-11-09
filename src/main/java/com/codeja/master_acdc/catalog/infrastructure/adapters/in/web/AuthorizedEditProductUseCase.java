package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.application.port.EditProductUseCase;
import com.codeja.master_acdc.catalog.domain.model.Product;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedEditProductUseCase implements EditProductUseCase {
  private final EditProductUseCase delegate;

  public AuthorizedEditProductUseCase(EditProductUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @PreAuthorize("hasAuthority('product:write')")
  public Product edit(UUID productId, String name, BigDecimal price, Long version) {
    return delegate.edit(productId, name, price, version);
  }
}
