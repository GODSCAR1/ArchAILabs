package com.codeja.master_acdc.inventory.domain.exception;

import java.util.UUID;

/** Señala que no se pudo adquirir a tiempo el bloqueo del stock. */
public class StockBusyException extends RuntimeException {

  public StockBusyException(UUID productId, Throwable cause) {
    super("stock is busy for product: " + productId, cause);
  }
}
