package com.codeja.master_acdc.inventory.domain.exception;

import java.util.UUID;

/** Señala que la liberación no encontró registro de stock para un producto reservado. */
public class StockRecordNotFoundException extends RuntimeException {

  public StockRecordNotFoundException(UUID productId) {
    super("stock record not found for product: " + productId);
  }
}
