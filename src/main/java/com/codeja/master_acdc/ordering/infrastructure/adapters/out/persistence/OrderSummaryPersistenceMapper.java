package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.OrderSummary;

public final class OrderSummaryPersistenceMapper {
  private OrderSummaryPersistenceMapper() {}

  public static OrderSummary toDomain(OrderSummaryRow row) {
    return new OrderSummary(
        row.id(),
        row.customerName(),
        Math.toIntExact(row.lineCount()),
        Math.toIntExact(row.totalUnits()));
  }
}
