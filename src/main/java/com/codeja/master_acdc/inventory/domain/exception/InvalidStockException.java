package com.codeja.master_acdc.inventory.domain.exception;

/** Señala que una línea de reserva de stock no es válida. */
public class InvalidStockException extends RuntimeException {

  public InvalidStockException(String message) {
    super(message);
  }
}
