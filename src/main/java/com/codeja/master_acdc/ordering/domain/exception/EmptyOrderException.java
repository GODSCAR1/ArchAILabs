package com.codeja.master_acdc.ordering.domain.exception;

/** Señala que el pedido no lleva ninguna línea. */
public class EmptyOrderException extends InvalidOrderException {

  public EmptyOrderException() {
    super("order must have at least one line");
  }
}
