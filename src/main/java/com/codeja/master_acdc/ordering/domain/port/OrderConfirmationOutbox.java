package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.Order;

/** Almacena la confirmación en la misma transacción que el cambio a PAID. */
public interface OrderConfirmationOutbox {
  void append(Order order);
}
