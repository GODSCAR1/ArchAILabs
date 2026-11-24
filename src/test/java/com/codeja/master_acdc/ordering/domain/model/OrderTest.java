package com.codeja.master_acdc.ordering.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;
import com.codeja.master_acdc.ordering.domain.event.OrderConfirmed;
import com.codeja.master_acdc.ordering.domain.exception.DuplicateProductException;
import com.codeja.master_acdc.ordering.domain.exception.EmptyOrderException;
import com.codeja.master_acdc.ordering.domain.exception.InvalidOrderException;
import com.codeja.master_acdc.ordering.domain.exception.InvalidQuantityException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotConfirmableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotRefundableException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderTest {

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PAID", "SHIPPED"})
  void should_refund_preserving_order_data_when_status_is_refundable(OrderStatus status) {
    Order order = Order.restore(UUID.randomUUID(), CUSTOMER, status, List.of(new OrderLine(A, 3)));
    order.refund();
    assertThat(order.customerId()).isEqualTo(CUSTOMER);
    assertThat(order.lines()).containsExactly(new OrderLine(A, 3));
    assertThat(order.status()).isEqualTo(OrderStatus.REFUNDED);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PENDING", "PAYING", "CANCELLED", "REFUNDED"})
  void should_reject_refund_when_status_is_not_refundable(OrderStatus status) {
    Order order = Order.restore(UUID.randomUUID(), CUSTOMER, status, List.of(new OrderLine(A, 3)));
    assertThatThrownBy(order::refund).isInstanceOf(OrderNotRefundableException.class);
  }

  @Test
  void should_reject_cancellation_when_status_is_refunded() {
    Order order =
        Order.restore(
            UUID.randomUUID(), CUSTOMER, OrderStatus.REFUNDED, List.of(new OrderLine(A, 3)));
    assertThatThrownBy(order::cancel).isInstanceOf(OrderNotCancellableException.class);
  }

  @Test
  @DisplayName("Cancelación mientras el pago está en curso")
  void should_reject_cancellation_when_status_is_paying() {
    Order order =
        Order.restore(
            UUID.randomUUID(), CUSTOMER, OrderStatus.PAYING, List.of(new OrderLine(A, 3)));
    assertThatThrownBy(order::cancel).isInstanceOf(OrderNotCancellableException.class);
  }

  private static final UUID CUSTOMER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Test
  void should_create_pending_order_when_lines_valid() {
    Order order = Order.place(CUSTOMER, List.of(new OrderLine(A, 3), new OrderLine(B, 2)));

    assertThat(order.id()).isNotNull();
    assertThat(order.customerId()).isEqualTo(CUSTOMER);
    assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.lines()).containsExactly(new OrderLine(A, 3), new OrderLine(B, 2));
  }

  @Test
  void should_reject_order_when_line_quantity_not_positive() {
    assertThatThrownBy(() -> Order.place(CUSTOMER, List.of(new OrderLine(A, 0))))
        .isInstanceOf(InvalidQuantityException.class);
    assertThatThrownBy(() -> Order.place(CUSTOMER, List.of(new OrderLine(A, -1))))
        .isInstanceOf(InvalidQuantityException.class);
  }

  @Test
  void should_reject_order_when_no_lines() {
    assertThatThrownBy(() -> Order.place(CUSTOMER, List.of()))
        .isInstanceOf(EmptyOrderException.class);
    assertThatThrownBy(() -> Order.place(CUSTOMER, null)).isInstanceOf(EmptyOrderException.class);
  }

  @Test
  @DisplayName("Un pedido requiere cliente")
  void should_reject_order_when_customer_is_null() {
    assertThatThrownBy(() -> Order.place(null, List.of(new OrderLine(A, 1))))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  @DisplayName("Las líneas del pedido no pueden alterarse desde fuera")
  void should_keep_lines_immutable_when_input_is_modified() {
    var lines = new ArrayList<>(List.of(new OrderLine(A, 3)));
    Order order = Order.place(CUSTOMER, lines);
    lines.clear();
    assertThat(order.lines()).containsExactly(new OrderLine(A, 3));
    assertThatThrownBy(() -> order.lines().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("La identidad de un pedido es su id")
  void should_compare_identity_when_other_data_differs() {
    UUID id = UUID.randomUUID();
    Order first = Order.restore(id, CUSTOMER, OrderStatus.PENDING, List.of(new OrderLine(A, 3)));
    Order second = Order.restore(id, UUID.randomUUID(), OrderStatus.PAID, List.of());
    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    assertThat(first).isNotEqualTo(Order.place(CUSTOMER, List.of(new OrderLine(A, 3))));
  }

  @Test
  @DisplayName("Confirmar un pedido pendiente inicia el pago")
  void should_start_payment_when_order_is_pending() {
    Order order = Order.place(CUSTOMER, List.of(new OrderLine(A, 3)));
    order.confirm();
    assertThat(order.status()).isEqualTo(OrderStatus.PAYING);
    assertThat(order.pullEvents()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PAYING", "PAID", "SHIPPED", "CANCELLED", "REFUNDED"})
  @DisplayName("No se confirma un pedido que no está pendiente")
  void should_reject_confirmation_when_order_is_not_pending(OrderStatus status) {
    Order order = Order.restore(UUID.randomUUID(), CUSTOMER, status, List.of(new OrderLine(A, 3)));
    assertThatThrownBy(order::confirm).isInstanceOf(OrderNotConfirmableException.class);
    assertThat(order.status()).isEqualTo(status);
    assertThat(order.pullEvents()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PENDING", "PAID", "SHIPPED", "CANCELLED", "REFUNDED"})
  @DisplayName("Solo se registra el pago en curso")
  void should_reject_payment_when_order_is_not_paying(OrderStatus status) {
    Order order = Order.restore(UUID.randomUUID(), CUSTOMER, status, List.of(new OrderLine(A, 3)));
    assertThatThrownBy(order::registerPayment).isInstanceOf(OrderNotConfirmableException.class);
    assertThat(order.status()).isEqualTo(status);
    assertThat(order.pullEvents()).isEmpty();
  }

  @Test
  @DisplayName("Los eventos se acumulan y se extraen una sola vez")
  void should_drain_events_when_payment_and_cancellation_are_registered() {
    Order order = Order.place(CUSTOMER, List.of(new OrderLine(A, 3), new OrderLine(B, 2)));
    order.confirm();
    order.registerPayment();
    assertThat(order.status()).isEqualTo(OrderStatus.PAID);
    order.cancel();
    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.pullEvents())
        .containsExactly(
            new OrderConfirmed(order.id(), List.of(A, B)),
            new OrderCancelled(order.id(), List.of(new OrderLine(A, 3), new OrderLine(B, 2))));
    assertThat(order.pullEvents()).isEmpty();
  }

  @Test
  void should_reject_order_when_product_repeated() {
    assertThatThrownBy(
            () -> Order.place(CUSTOMER, List.of(new OrderLine(A, 2), new OrderLine(A, 3))))
        .isInstanceOf(DuplicateProductException.class);
  }

  @Test
  void should_return_cancelled_order_when_cancel_from_pending() {
    Order order = Order.place(CUSTOMER, List.of(new OrderLine(A, 3)));

    order.cancel();

    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.customerId()).isEqualTo(CUSTOMER);
    assertThat(order.lines()).containsExactly(new OrderLine(A, 3));
  }

  @Test
  void should_return_cancelled_order_when_cancel_from_paid() {
    Order order =
        Order.restore(UUID.randomUUID(), CUSTOMER, OrderStatus.PAID, List.of(new OrderLine(A, 3)));

    order.cancel();

    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void should_throw_order_not_cancellable_when_cancel_from_shipped() {
    Order order =
        Order.restore(
            UUID.randomUUID(), CUSTOMER, OrderStatus.SHIPPED, List.of(new OrderLine(A, 3)));

    assertThatThrownBy(order::cancel).isInstanceOf(OrderNotCancellableException.class);
  }

  @Test
  void should_throw_order_not_cancellable_when_cancel_from_cancelled() {
    Order order =
        Order.restore(
            UUID.randomUUID(), CUSTOMER, OrderStatus.CANCELLED, List.of(new OrderLine(A, 3)));

    assertThatThrownBy(order::cancel).isInstanceOf(OrderNotCancellableException.class);
  }
}
