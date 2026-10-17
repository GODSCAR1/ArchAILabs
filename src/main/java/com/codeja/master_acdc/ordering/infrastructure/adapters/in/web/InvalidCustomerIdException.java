package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

/** Indica que la cabecera {@code X-Customer-Id} no es un UUID. */
public class InvalidCustomerIdException extends RuntimeException {

  public InvalidCustomerIdException() {
    super("X-Customer-Id header must be a UUID");
  }
}
