package com.codeja.master_acdc.ordering.domain.exception;

import java.util.UUID;

public class OrderNotConfirmableException extends RuntimeException {
  public OrderNotConfirmableException(UUID id) {
    super("order not confirmable: " + id);
  }
}
