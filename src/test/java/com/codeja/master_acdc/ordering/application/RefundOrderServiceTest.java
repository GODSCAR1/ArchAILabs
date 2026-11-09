package com.codeja.master_acdc.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotRefundableException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RefundOrderServiceTest {
  private final UUID id = UUID.randomUUID();
  private final OrderRepository orders = mock(OrderRepository.class);
  private final RefundOrderService service = new RefundOrderService(orders);

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PAID", "SHIPPED"})
  void should_refund_without_other_effects_when_conditional_update_wins(OrderStatus status) {
    Order order = givenOrder(status);
    when(orders.refundIfRefundable(id)).thenReturn(true);
    assertThat(service.refund(id)).isEqualTo(order.refund());
    verify(orders).findVisibleById(id, OrderReadScope.Global.INSTANCE);
    verify(orders).refundIfRefundable(id);
    verifyNoMoreInteractions(orders);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PENDING", "CANCELLED", "REFUNDED"})
  void should_reject_without_update_when_status_is_not_refundable(OrderStatus status) {
    givenOrder(status);
    assertThatThrownBy(() -> service.refund(id)).isInstanceOf(OrderNotRefundableException.class);
    verify(orders).findVisibleById(id, OrderReadScope.Global.INSTANCE);
    verifyNoMoreInteractions(orders);
  }

  @Test
  void should_report_conflict_when_valid_read_is_followed_by_zero_updated_rows() {
    givenOrder(OrderStatus.PAID);
    when(orders.refundIfRefundable(id)).thenReturn(false);
    assertThatThrownBy(() -> service.refund(id)).isInstanceOf(OrderNotRefundableException.class);
  }

  @Test
  void should_report_not_found_when_order_is_absent() {
    when(orders.findVisibleById(id, OrderReadScope.Global.INSTANCE)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.refund(id)).isInstanceOf(OrderNotFoundException.class);
    verify(orders).findVisibleById(id, OrderReadScope.Global.INSTANCE);
    verifyNoMoreInteractions(orders);
  }

  private Order givenOrder(OrderStatus status) {
    Order order =
        new Order(id, UUID.randomUUID(), status, List.of(new OrderLine(UUID.randomUUID(), 2)));
    when(orders.findVisibleById(id, OrderReadScope.Global.INSTANCE)).thenReturn(Optional.of(order));
    return order;
  }
}
