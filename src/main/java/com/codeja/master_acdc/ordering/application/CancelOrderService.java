package com.codeja.master_acdc.ordering.application;

import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.port.OrderCancelledPublisher;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.UUID;

public class CancelOrderService implements CancelOrderUseCase {

  private final OrderRepository orderRepository;
  private final OrderCancelledPublisher orderCancelledPublisher;

  public CancelOrderService(
      OrderRepository orderRepository, OrderCancelledPublisher orderCancelledPublisher) {
    this.orderRepository = orderRepository;
    this.orderCancelledPublisher = orderCancelledPublisher;
  }

  @Override
  public Order cancel(UUID orderId, UUID customerId) {
    Order order =
        orderRepository
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

    Order cancelled = order.cancel();

    if (!orderRepository.cancelIfCancelable(orderId, customerId)) {
      throw new OrderNotCancellableException(orderId);
    }

    orderCancelledPublisher.publish(new OrderCancelled(orderId, cancelled.lines()));
    return cancelled;
  }
}
