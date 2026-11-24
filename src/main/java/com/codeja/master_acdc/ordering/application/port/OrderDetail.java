package com.codeja.master_acdc.ordering.application.port;

import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import java.util.List;
import java.util.UUID;

public record OrderDetail(UUID id, OrderStatus status, List<OrderLine> lines) {
  public OrderDetail {
    lines = List.copyOf(lines);
  }
}
