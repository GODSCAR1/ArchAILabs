package com.codeja.master_acdc.inventory.domain.model;

import com.codeja.master_acdc.inventory.domain.exception.InvalidStockException;
import java.util.UUID;

/** Resultado de reservar unidades de un producto. */
public record ProductReservation(UUID productId, int units, int available) {

  public ProductReservation {
    if (productId == null) {
      throw new InvalidStockException("product id must not be null");
    }
    if (units <= 0) {
      throw new InvalidStockException("units must be greater than 0");
    }
    if (available < 0) {
      throw new InvalidStockException("available stock must not be negative");
    }
  }
}
