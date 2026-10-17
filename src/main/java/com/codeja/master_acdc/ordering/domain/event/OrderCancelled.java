package com.codeja.master_acdc.ordering.domain.event;

import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import java.util.List;
import java.util.UUID;

/** Hecho de negocio: un pedido ha sido cancelado y su stock debe liberarse. */
public record OrderCancelled(UUID orderId, List<OrderLine> lines) {

  public OrderCancelled {
    lines = List.copyOf(lines);
  }
}
