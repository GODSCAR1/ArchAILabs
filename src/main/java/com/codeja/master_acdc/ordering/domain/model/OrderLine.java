package com.codeja.master_acdc.ordering.domain.model;

import com.codeja.master_acdc.ordering.domain.exception.InvalidLineException;
import com.codeja.master_acdc.ordering.domain.exception.InvalidQuantityException;
import java.util.UUID;

/** Línea de pedido: producto y cantidad positiva. */
public record OrderLine(UUID productId, int quantity) {

  public OrderLine {
    if (productId == null) {
      throw new InvalidLineException();
    }
    if (quantity <= 0) {
      throw new InvalidQuantityException();
    }
  }
}
