package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;

/** Puerto de salida para publicar el evento de cancelación de un pedido. */
public interface OrderCancelledPublisher {

  void publish(OrderCancelled event);
}
