package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import java.util.List;
import java.util.UUID;

/** Cuerpo de la respuesta de creación de pedido. */
public record OrderResponse(UUID id, UUID customerId, OrderStatus status, List<Line> lines) {

  public record Line(UUID productId, int quantity) {}

  static OrderResponse from(Order order) {
    List<Line> lines =
        order.lines().stream().map(line -> new Line(line.productId(), line.quantity())).toList();
    return new OrderResponse(order.id(), order.customerId(), order.status(), lines);
  }
}
