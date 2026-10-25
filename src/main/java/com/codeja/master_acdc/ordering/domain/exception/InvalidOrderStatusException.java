package com.codeja.master_acdc.ordering.domain.exception;

public class InvalidOrderStatusException extends RuntimeException {

  public InvalidOrderStatusException(String status) {
    super("invalid order status: " + status);
  }
}
