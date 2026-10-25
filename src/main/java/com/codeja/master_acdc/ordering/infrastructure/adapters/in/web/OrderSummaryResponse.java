package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import java.util.UUID;

public record OrderSummaryResponse(UUID id, String customerName, int lineCount, int totalUnits) {

  static OrderSummaryResponse from(OrderSummary summary) {
    return new OrderSummaryResponse(
        summary.id(), summary.customerName(), summary.lineCount(), summary.totalUnits());
  }
}
