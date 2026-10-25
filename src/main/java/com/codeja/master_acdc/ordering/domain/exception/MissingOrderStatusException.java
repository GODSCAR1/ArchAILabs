package com.codeja.master_acdc.ordering.domain.exception;

public class MissingOrderStatusException extends RuntimeException {

  public MissingOrderStatusException() {
    super("order status is required");
  }
}
