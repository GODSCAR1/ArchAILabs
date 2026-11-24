package com.codeja.master_acdc.ordering.application.port;

import com.codeja.master_acdc.ordering.domain.model.RequestedOrderLine;
import java.util.List;
import java.util.UUID;

public record PlaceOrderCommand(UUID customerId, List<RequestedOrderLine> lines) {
  public PlaceOrderCommand {
    lines = List.copyOf(lines);
  }
}
