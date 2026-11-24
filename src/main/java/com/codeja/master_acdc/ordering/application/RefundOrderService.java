package com.codeja.master_acdc.ordering.application;

import com.codeja.master_acdc.ordering.application.port.OrderDetail;
import com.codeja.master_acdc.ordering.application.port.RefundOrderCommand;
import com.codeja.master_acdc.ordering.application.port.RefundOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotRefundableException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.UUID;

public class RefundOrderService implements RefundOrderUseCase {
  private final OrderRepository orders;

  public RefundOrderService(OrderRepository orders) {
    this.orders = orders;
  }

  @Override
  public OrderDetail refund(RefundOrderCommand command) {
    UUID orderId = command.orderId();
    Order order =
        orders
            .findVisibleById(orderId, OrderReadScope.Global.INSTANCE)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    order.refund();
    if (!orders.refundIfRefundable(orderId)) {
      throw new OrderNotRefundableException(orderId);
    }
    return new OrderDetail(order.id(), order.status(), order.lines());
  }
}
