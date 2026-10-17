package com.codeja.master_acdc.ordering.domain.exception;

/** Señala que una línea tiene cantidad no positiva. */
public class InvalidQuantityException extends InvalidOrderException {

  public InvalidQuantityException() {
    super("quantity must be greater than 0");
  }
}
