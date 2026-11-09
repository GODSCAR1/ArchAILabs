package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.model.RequestedOrderLine;
import java.util.List;
import java.util.UUID;

public final class OrderWebMapper {
  private OrderWebMapper() {}

  public static List<RequestedOrderLine> toLines(CreateOrderRequest request) {
    if (request == null || request.lines() == null) {
      return List.of();
    }
    return request.lines().stream()
        .map(
            line ->
                line == null
                    ? new RequestedOrderLine(null, 0)
                    : new RequestedOrderLine(line.productId(), line.quantity()))
        .toList();
  }

  public static UUID orderId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      // The authorized facade rejects malformed ids after checking permissions.
      return null;
    }
  }

  public static OrderResponse response(Order order) {
    return new OrderResponse(
        order.id(),
        order.status(),
        order.lines().stream()
            .map(line -> new OrderResponse.Line(line.productId(), line.quantity()))
            .toList());
  }

  public static OrderSummaryResponse summary(OrderSummary order) {
    return new OrderSummaryResponse(
        order.id(), order.customerName(), order.lineCount(), order.totalUnits());
  }
}
