package com.codeja.master_acdc.ordering.application;

import com.codeja.master_acdc.ordering.application.port.CancelOrderCommand;
import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.OrderDetail;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.PublishOrderEvents;
import java.util.UUID;

public class CancelOrderService implements CancelOrderUseCase {

  private final OrderRepository orderRepository;
  private final PublishOrderEvents orderEvents;

  public CancelOrderService(OrderRepository orderRepository, PublishOrderEvents orderEvents) {
    this.orderRepository = orderRepository;
    this.orderEvents = orderEvents;
  }

  @Override
  public OrderDetail cancel(CancelOrderCommand command) {
    UUID orderId = command.orderId();
    UUID customerId = command.customerId();
    Order order =
        orderRepository
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

    order.cancel();

    if (!orderRepository.cancelIfCancelable(orderId, customerId)) {
      throw new OrderNotCancellableException(orderId);
    }

    var events = order.pullEvents();
    if (!events.isEmpty()) {
      orderEvents.publish(events);
    }
    return new OrderDetail(order.id(), order.status(), order.lines());
  }
}
