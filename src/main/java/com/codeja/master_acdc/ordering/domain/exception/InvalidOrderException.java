package com.codeja.master_acdc.ordering.domain.exception;

/** Señala que un pedido no es válido. */
public class InvalidOrderException extends RuntimeException {

  public InvalidOrderException(String message) {
    super(message);
  }
}
