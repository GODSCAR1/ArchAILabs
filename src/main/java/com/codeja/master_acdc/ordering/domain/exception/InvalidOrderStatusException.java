package com.codeja.master_acdc.ordering.domain.exception;

public class InvalidOrderStatusException extends RuntimeException {

  public InvalidOrderStatusException(String status) {
    super("invalid order status: " + status);
  }

  public InvalidOrderStatusException(String status, Throwable cause) {
    super("invalid order status: " + status, cause);
  }
}
