package com.codeja.master_acdc.domain.model;

import java.util.UUID;

/** Producto del catálogo. El identificador lo genera la aplicación, no la base de datos. */
public record Product(UUID id, ProductName name, Sku sku, Price price) {

  public static Product create(ProductName name, Sku sku, Price price) {
    return new Product(UUID.randomUUID(), name, sku, price);
  }
}
