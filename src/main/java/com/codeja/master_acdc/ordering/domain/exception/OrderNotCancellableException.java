package com.codeja.master_acdc.ordering.domain.exception;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import java.util.UUID;

/** Señala que el pedido no está en un estado cancelable (SHIPPED/CANCELLED o carrera perdida). */
public class OrderNotCancellableException extends RuntimeException {

  public OrderNotCancellableException(UUID orderId, OrderStatus status) {
    super("order not cancellable: " + orderId + " in status " + status);
  }

  public OrderNotCancellableException(UUID orderId) {
    super("order not cancellable: " + orderId);
  }
}
