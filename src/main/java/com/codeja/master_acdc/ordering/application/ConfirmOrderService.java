package com.codeja.master_acdc.ordering.application;

import com.codeja.master_acdc.ordering.application.port.ConfirmOrderCommand;
import com.codeja.master_acdc.ordering.application.port.ConfirmOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.OrderDetail;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentDeclinedException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentTimeoutException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentUnavailableException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.PaymentResult;
import com.codeja.master_acdc.ordering.domain.port.OrderAmountCalculator;
import com.codeja.master_acdc.ordering.domain.port.OrderPayments;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.PaymentGateway;
import java.util.UUID;

public final class ConfirmOrderService implements ConfirmOrderUseCase {
  private final OrderRepository orders;
  private final OrderPayments payments;
  private final OrderAmountCalculator amounts;
  private final PaymentGateway gateway;

  public ConfirmOrderService(
      OrderRepository orders,
      OrderPayments payments,
      OrderAmountCalculator amounts,
      PaymentGateway gateway) {
    this.orders = orders;
    this.payments = payments;
    this.amounts = amounts;
    this.gateway = gateway;
  }

  @Override
  public OrderDetail confirm(ConfirmOrderCommand command) {
    UUID orderId = command.orderId();
    UUID customerId = command.customerId();
    Order order =
        orders
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    order.confirm();
    var amount =
        payments.fixedAmount(orderId, customerId).orElseGet(() -> amounts.calculate(order.lines()));
    var attempt = payments.startAttempt(orderId, customerId, amount);
    PaymentResult result;
    try {
      result = gateway.authorize(orderId, attempt);
    } catch (PaymentTimeoutException | PaymentUnavailableException ex) {
      payments.abandonAttempt(orderId, customerId, false);
      throw ex;
    }
    if (result == PaymentResult.DECLINED) {
      payments.abandonAttempt(orderId, customerId, true);
      throw new PaymentDeclinedException();
    }
    order.registerPayment();
    payments.settle(order);
    return new OrderDetail(order.id(), order.status(), order.lines());
  }
}
