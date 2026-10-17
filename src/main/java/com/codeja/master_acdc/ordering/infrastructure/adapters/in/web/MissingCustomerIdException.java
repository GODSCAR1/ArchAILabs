package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

/** Indica que falta la cabecera {@code X-Customer-Id}. */
public class MissingCustomerIdException extends RuntimeException {

  public MissingCustomerIdException() {
    super("X-Customer-Id header is required");
  }
}
