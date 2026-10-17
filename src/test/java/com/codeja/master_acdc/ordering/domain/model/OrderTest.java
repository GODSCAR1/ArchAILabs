package com.codeja.master_acdc.ordering.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.ordering.domain.exception.DuplicateProductException;
import com.codeja.master_acdc.ordering.domain.exception.EmptyOrderException;
import com.codeja.master_acdc.ordering.domain.exception.InvalidQuantityException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

  private static final UUID CUSTOMER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Test
  void should_create_pending_order_when_lines_valid() {
    Order order = Order.create(CUSTOMER, List.of(new OrderLine(A, 3), new OrderLine(B, 2)));

    assertThat(order.id()).isNotNull();
    assertThat(order.customerId()).isEqualTo(CUSTOMER);
    assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.lines()).containsExactly(new OrderLine(A, 3), new OrderLine(B, 2));
  }

  @Test
  void should_reject_order_when_line_quantity_not_positive() {
    assertThatThrownBy(() -> Order.create(CUSTOMER, List.of(new OrderLine(A, 0))))
        .isInstanceOf(InvalidQuantityException.class);
    assertThatThrownBy(() -> Order.create(CUSTOMER, List.of(new OrderLine(A, -1))))
        .isInstanceOf(InvalidQuantityException.class);
  }

  @Test
  void should_reject_order_when_no_lines() {
    assertThatThrownBy(() -> Order.create(CUSTOMER, List.of()))
        .isInstanceOf(EmptyOrderException.class);
  }

  @Test
  void should_reject_order_when_product_repeated() {
    assertThatThrownBy(
            () -> Order.create(CUSTOMER, List.of(new OrderLine(A, 2), new OrderLine(A, 3))))
        .isInstanceOf(DuplicateProductException.class);
  }

  @Test
  void should_return_cancelled_order_when_cancel_from_pending() {
    Order order = Order.create(CUSTOMER, List.of(new OrderLine(A, 3)));

    Order cancelled = order.cancel();

    assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(cancelled.id()).isEqualTo(order.id());
    assertThat(cancelled.customerId()).isEqualTo(CUSTOMER);
    assertThat(cancelled.lines()).containsExactly(new OrderLine(A, 3));
  }

  @Test
  void should_return_cancelled_order_when_cancel_from_paid() {
    Order order =
        new Order(UUID.randomUUID(), CUSTOMER, OrderStatus.PAID, List.of(new OrderLine(A, 3)));

    Order cancelled = order.cancel();

    assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void should_throw_order_not_cancellable_when_cancel_from_shipped() {
    Order order =
        new Order(UUID.randomUUID(), CUSTOMER, OrderStatus.SHIPPED, List.of(new OrderLine(A, 3)));

    assertThatThrownBy(order::cancel).isInstanceOf(OrderNotCancellableException.class);
  }

  @Test
  void should_throw_order_not_cancellable_when_cancel_from_cancelled() {
    Order order =
        new Order(UUID.randomUUID(), CUSTOMER, OrderStatus.CANCELLED, List.of(new OrderLine(A, 3)));

    assertThatThrownBy(order::cancel).isInstanceOf(OrderNotCancellableException.class);
  }
}
