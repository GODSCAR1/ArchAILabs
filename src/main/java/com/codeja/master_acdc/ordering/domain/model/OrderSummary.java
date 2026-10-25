package com.codeja.master_acdc.ordering.domain.model;

import java.util.List;
import java.util.UUID;

public record OrderSummary(
    UUID id, String customerName, int lineCount, int totalUnits, List<UUID> productIds) {

  public OrderSummary {
    productIds = List.copyOf(productIds);
  }
}
