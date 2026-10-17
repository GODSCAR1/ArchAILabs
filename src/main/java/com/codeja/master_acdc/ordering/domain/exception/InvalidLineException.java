package com.codeja.master_acdc.ordering.domain.exception;

/** Señala que una línea no lleva producto. */
public class InvalidLineException extends InvalidOrderException {

  public InvalidLineException() {
    super("product id must not be null");
  }
}
