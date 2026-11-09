package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import java.util.List;
import java.util.UUID;

/** Cuerpo de la respuesta de creación de pedido. */
public record OrderResponse(UUID id, OrderStatus status, List<Line> lines) {

  public record Line(UUID productId, int quantity) {}
}
