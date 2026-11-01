package com.codeja.master_acdc.inventory.domain.model;

import com.codeja.master_acdc.inventory.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.inventory.domain.exception.InvalidStockException;
import java.util.UUID;

/** Balance disponible de stock para un producto. */
public record StockBalance(UUID productId, int available) {

  public StockBalance {
    if (productId == null) {
      throw new InvalidStockException("product id must not be null");
    }
    if (available < 0) {
      throw new InvalidStockException("available stock must not be negative");
    }
  }

  public StockBalance reserve(int units) {
    if (units <= 0) {
      throw new InvalidStockException("units must be greater than 0");
    }
    if (units > available) {
      throw new InsufficientStockException(productId, available);
    }
    return new StockBalance(productId, available - units);
  }
}
