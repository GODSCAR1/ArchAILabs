package com.codeja.master_acdc.ordering.domain.exception;

/** Señala que la reserva de stock referenció un producto sin registro de inventario. */
public class UnknownProductException extends RuntimeException {

  public UnknownProductException() {
    super("unknown product");
  }
}
