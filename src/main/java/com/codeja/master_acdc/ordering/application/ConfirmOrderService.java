package com.codeja.master_acdc.ordering.application;

import com.codeja.master_acdc.ordering.application.port.ConfirmOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotConfirmableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentDeclinedException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentTimeoutException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentUnavailableException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.PaymentResult;
import com.codeja.master_acdc.ordering.domain.port.OrderAmountCalculator;
import com.codeja.master_acdc.ordering.domain.port.OrderPaymentRepository;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.PaymentGateway;
import com.codeja.master_acdc.ordering.domain.port.PaymentTransactions;
import java.util.UUID;

public final class ConfirmOrderService implements ConfirmOrderUseCase {
  private final OrderRepository orders;
  private final OrderPaymentRepository payments;
  private final OrderAmountCalculator amounts;
  private final PaymentGateway gateway;
  private final PaymentTransactions transactions;

  public ConfirmOrderService(
      OrderRepository orders,
      OrderPaymentRepository payments,
      OrderAmountCalculator amounts,
      PaymentGateway gateway,
      PaymentTransactions transactions) {
    this.orders = orders;
    this.payments = payments;
    this.amounts = amounts;
    this.gateway = gateway;
    this.transactions = transactions;
  }

  @Override
  public Order confirm(UUID orderId, UUID customerId) {
    Order order =
        orders
            .findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    if (order.status() != OrderStatus.PENDING) {
      throw new OrderNotConfirmableException(orderId);
    }
    var amount =
        payments.findAmount(orderId, customerId).orElseGet(() -> amounts.calculate(order.lines()));
    var attempt = transactions.begin(orderId, customerId, amount);
    PaymentResult result;
    try {
      result = gateway.authorize(orderId, attempt);
    } catch (PaymentTimeoutException | PaymentUnavailableException ex) {
      transactions.failed(orderId, customerId, false);
      throw ex;
    }
    if (result == PaymentResult.DECLINED) {
      transactions.failed(orderId, customerId, true);
      throw new PaymentDeclinedException();
    }
    return transactions.complete(order);
  }
}
