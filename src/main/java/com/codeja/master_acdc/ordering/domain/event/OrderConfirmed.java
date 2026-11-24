package com.codeja.master_acdc.ordering.domain.event;

import java.util.List;
import java.util.UUID;

public record OrderConfirmed(UUID orderId, List<UUID> productIds) implements OrderEvent {
  public OrderConfirmed {
    productIds = List.copyOf(productIds);
  }
}
