package com.codeja.master_acdc.inventory.domain.exception;

import java.util.UUID;

/** Señala que el stock disponible no alcanza para la reserva solicitada. */
public class InsufficientStockException extends RuntimeException {

  private final int available;

  public InsufficientStockException(UUID productId, int available) {
    super("insufficient stock for product: " + productId);
    this.available = available;
  }

  public int available() {
    return available;
  }
}
