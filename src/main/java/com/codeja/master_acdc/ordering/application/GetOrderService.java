package com.codeja.master_acdc.ordering.application;

import com.codeja.master_acdc.ordering.application.port.GetOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.UUID;

public class GetOrderService implements GetOrderUseCase {
  private final OrderRepository orders;

  public GetOrderService(OrderRepository orders) {
    this.orders = orders;
  }

  @Override
  public Order get(UUID orderId, OrderReadScope scope) {
    if (orderId == null || scope == OrderReadScope.None.INSTANCE) {
      throw new OrderNotFoundException(orderId);
    }
    return orders
        .findVisibleById(orderId, scope)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }
}
