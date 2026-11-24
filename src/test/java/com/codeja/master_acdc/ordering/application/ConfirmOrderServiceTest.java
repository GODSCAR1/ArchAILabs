package com.codeja.master_acdc.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.ordering.application.port.ConfirmOrderCommand;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotConfirmableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentDeclinedException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentTimeoutException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.model.PaymentResult;
import com.codeja.master_acdc.ordering.domain.port.OrderAmountCalculator;
import com.codeja.master_acdc.ordering.domain.port.OrderPayments;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.PaymentGateway;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfirmOrderServiceTest {
  private final UUID id = UUID.randomUUID();
  private final UUID customer = UUID.randomUUID();
  private final Order order =
      Order.restore(
          id, customer, OrderStatus.PENDING, List.of(new OrderLine(UUID.randomUUID(), 2)));
  private final PaymentAttempt attempt = new PaymentAttempt(new BigDecimal("100"), 0);
  private final OrderRepository orders = mock(OrderRepository.class);
  private final OrderPayments payments = mock(OrderPayments.class);
  private final OrderAmountCalculator amounts = mock(OrderAmountCalculator.class);
  private final PaymentGateway gateway = mock(PaymentGateway.class);
  private final ConfirmOrderService service =
      new ConfirmOrderService(orders, payments, amounts, gateway);

  @Test
  @DisplayName("El reintento reutiliza el importe ya fijado")
  void should_reuse_amount_when_payment_was_attempted_before() {
    prepare();
    when(payments.fixedAmount(id, customer)).thenReturn(Optional.of(attempt.amount()));
    when(gateway.authorize(id, attempt)).thenReturn(PaymentResult.AUTHORIZED);
    Order paid = Order.restore(id, customer, OrderStatus.PAID, order.lines());

    var result = service.confirm(new ConfirmOrderCommand(id, customer));
    assertThat(result.id()).isEqualTo(paid.id());
    assertThat(result.status()).isEqualTo(OrderStatus.PAID);
    assertThat(result.lines()).isEqualTo(paid.lines());
    assertThat(order.status()).isEqualTo(OrderStatus.PAID);
    verify(payments).settle(order);
    verify(amounts, never()).calculate(any());
  }

  @Test
  @DisplayName("El rechazo devuelve el pedido a pendiente con nuevo intento")
  void should_increment_attempt_when_payment_is_declined() {
    prepare();
    when(amounts.calculate(order.lines())).thenReturn(attempt.amount());
    when(gateway.authorize(id, attempt)).thenReturn(PaymentResult.DECLINED);

    assertThatThrownBy(() -> service.confirm(new ConfirmOrderCommand(id, customer)))
        .isInstanceOf(PaymentDeclinedException.class);
    verify(payments).abandonAttempt(id, customer, true);
  }

  @Test
  @DisplayName("Un timeout conserva el mismo intento")
  void should_preserve_attempt_when_gateway_times_out() {
    prepare();
    when(amounts.calculate(order.lines())).thenReturn(attempt.amount());
    when(gateway.authorize(id, attempt))
        .thenThrow(new PaymentTimeoutException(new java.net.SocketTimeoutException()));

    assertThatThrownBy(() -> service.confirm(new ConfirmOrderCommand(id, customer)))
        .isInstanceOf(PaymentTimeoutException.class);
    verify(payments).abandonAttempt(id, customer, false);
  }

  @Test
  @DisplayName("Un pedido ajeno o inexistente no inicia autorización")
  void should_reject_confirmation_when_order_is_not_visible() {
    assertThatThrownBy(() -> service.confirm(new ConfirmOrderCommand(id, customer)))
        .isInstanceOf(OrderNotFoundException.class);
    verify(gateway, never()).authorize(any(), any());
  }

  @Test
  @DisplayName("Un pedido no pendiente no inicia autorización")
  void should_reject_confirmation_when_order_is_not_pending() {
    when(orders.findByIdAndCustomerId(id, customer))
        .thenReturn(Optional.of(Order.restore(id, customer, OrderStatus.PAYING, order.lines())));

    assertThatThrownBy(() -> service.confirm(new ConfirmOrderCommand(id, customer)))
        .isInstanceOf(OrderNotConfirmableException.class);
    verify(gateway, never()).authorize(any(), any());
  }

  private void prepare() {
    when(orders.findByIdAndCustomerId(id, customer)).thenReturn(Optional.of(order));
    when(payments.startAttempt(id, customer, attempt.amount())).thenReturn(attempt);
  }
}
