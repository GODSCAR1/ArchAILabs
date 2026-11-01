package com.codeja.master_acdc.catalog.domain.exception;

import java.util.UUID;

/** Señala que el producto solicitado no existe. */
public class ProductNotFoundException extends RuntimeException {

  public ProductNotFoundException(UUID productId) {
    super("product not found: " + productId);
  }
}
