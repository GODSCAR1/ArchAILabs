package com.codeja.master_acdc.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.port.OrderCancelledPublisher;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CancelOrderServiceTest {

  private static final UUID ORDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID CUSTOMER_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
  private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final OrderCancelledPublisher publisher = mock(OrderCancelledPublisher.class);
  private final CancelOrderService service = new CancelOrderService(orderRepository, publisher);

  @Test
  void should_cancel_and_publish_when_order_pending() {
    Order pending =
        new Order(ORDER_ID, CUSTOMER_ID, OrderStatus.PENDING, List.of(new OrderLine(A, 3)));
    given(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
        .willReturn(Optional.of(pending));
    given(orderRepository.cancelIfCancelable(ORDER_ID, CUSTOMER_ID)).willReturn(true);

    Order result = service.cancel(ORDER_ID, CUSTOMER_ID);

    assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
    then(publisher).should().publish(any(OrderCancelled.class));
  }

  @Test
  void should_report_not_found_when_order_absent_or_foreign() {
    given(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancel(ORDER_ID, CUSTOMER_ID))
        .isInstanceOf(OrderNotFoundException.class);
    then(publisher).should(never()).publish(any());
    then(orderRepository).should(never()).cancelIfCancelable(any(), any());
  }

  @Test
  void should_report_not_cancellable_when_order_shipped() {
    Order shipped =
        new Order(ORDER_ID, CUSTOMER_ID, OrderStatus.SHIPPED, List.of(new OrderLine(A, 3)));
    given(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
        .willReturn(Optional.of(shipped));

    assertThatThrownBy(() -> service.cancel(ORDER_ID, CUSTOMER_ID))
        .isInstanceOf(OrderNotCancellableException.class);
    then(publisher).should(never()).publish(any());
    then(orderRepository).should(never()).cancelIfCancelable(any(), any());
  }

  @Test
  void should_report_not_cancellable_when_update_matches_no_row() {
    Order pending =
        new Order(ORDER_ID, CUSTOMER_ID, OrderStatus.PENDING, List.of(new OrderLine(A, 3)));
    given(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
        .willReturn(Optional.of(pending));
    given(orderRepository.cancelIfCancelable(ORDER_ID, CUSTOMER_ID)).willReturn(false);

    assertThatThrownBy(() -> service.cancel(ORDER_ID, CUSTOMER_ID))
        .isInstanceOf(OrderNotCancellableException.class);
    then(publisher).should(never()).publish(any());
  }

  @Test
  void should_not_publish_when_order_not_found() {
    given(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancel(ORDER_ID, CUSTOMER_ID))
        .isInstanceOf(OrderNotFoundException.class);
    then(publisher).shouldHaveNoInteractions();
  }
}
