package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import java.util.UUID;

public final class OrderSummaryPersistenceMapper {
  private OrderSummaryPersistenceMapper() {}

  public static OrderSummary fromNativeRow(Object[] values) {
    return toDomain(
        new OrderSummaryRow(
            (UUID) values[0],
            (String) values[1],
            ((Number) values[2]).longValue(),
            ((Number) values[3]).longValue()));
  }

  public static OrderSummary toDomain(OrderSummaryRow row) {
    return new OrderSummary(
        row.id(),
        row.customerName(),
        Math.toIntExact(row.lineCount()),
        Math.toIntExact(row.totalUnits()));
  }
}
