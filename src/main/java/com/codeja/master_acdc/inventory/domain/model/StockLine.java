package com.codeja.master_acdc.inventory.domain.model;

import com.codeja.master_acdc.inventory.domain.exception.InvalidStockException;
import java.util.UUID;

/** Línea de reserva: producto y cantidad positiva a reservar. */
public record StockLine(UUID productId, int quantity) {

  public StockLine {
    if (productId == null) {
      throw new InvalidStockException("product id must not be null");
    }
    if (quantity <= 0) {
      throw new InvalidStockException("quantity must be greater than 0");
    }
  }
}
