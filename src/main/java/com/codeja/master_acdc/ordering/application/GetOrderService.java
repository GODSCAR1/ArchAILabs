package com.codeja.master_acdc.ordering.application;

import com.codeja.master_acdc.ordering.application.port.GetOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.UUID;

public class GetOrderService implements GetOrderUseCase {

  private final OrderRepository orderRepository;

  public GetOrderService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Override
  public Order get(UUID orderId, UUID customerId) {
    return orderRepository
        .findByIdAndCustomerId(orderId, customerId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }
}
