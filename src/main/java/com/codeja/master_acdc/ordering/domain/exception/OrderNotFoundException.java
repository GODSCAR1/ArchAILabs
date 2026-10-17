package com.codeja.master_acdc.ordering.domain.exception;

import java.util.UUID;

/** Señala que el pedido no existe o no pertenece al cliente (sin distinguir entre ambos). */
public class OrderNotFoundException extends RuntimeException {

  public OrderNotFoundException(UUID orderId) {
    super("order not found: " + orderId);
  }
}
