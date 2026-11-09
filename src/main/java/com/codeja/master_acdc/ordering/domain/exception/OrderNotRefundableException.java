package com.codeja.master_acdc.ordering.domain.exception;

import java.util.UUID;

public class OrderNotRefundableException extends RuntimeException {
  public OrderNotRefundableException(UUID orderId) {
    super("order not refundable: " + orderId);
  }
}
