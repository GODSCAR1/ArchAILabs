package com.codeja.master_acdc.infrastructure.adapters.in.web;

import com.codeja.master_acdc.domain.model.Product;
import java.util.UUID;

/** Cuerpo de la respuesta de creación, con los valores ya normalizados. */
public record CreateProductResponse(UUID id, String name, long price, String sku) {

  static CreateProductResponse from(Product product) {
    return new CreateProductResponse(
        product.id(), product.name().value(), product.price().value(), product.sku().value());
  }
}
