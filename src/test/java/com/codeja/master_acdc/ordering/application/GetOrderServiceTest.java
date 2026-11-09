package com.codeja.master_acdc.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetOrderServiceTest {
  private final OrderRepository orders = mock(OrderRepository.class);
  private final GetOrderService service = new GetOrderService(orders);

  @Test
  void should_return_visible_order_when_repository_matches_scope() {
    var order =
        new Order(
            UUID.randomUUID(),
            UUID.randomUUID(),
            OrderStatus.PAID,
            List.of(new OrderLine(UUID.randomUUID(), 2)));
    var scope = new OrderReadScope.Own(order.customerId());
    given(orders.findVisibleById(order.id(), scope)).willReturn(Optional.of(order));
    assertThat(service.get(order.id(), scope)).isEqualTo(order);
  }

  @Test
  void should_hide_existence_when_order_is_missing_or_outside_scope() {
    UUID id = UUID.randomUUID();
    given(orders.findVisibleById(id, OrderReadScope.Global.INSTANCE)).willReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(id, OrderReadScope.Global.INSTANCE))
        .isInstanceOf(OrderNotFoundException.class);
  }

  @Test
  void should_skip_repository_when_id_is_invalid_or_scope_is_empty() {
    assertThatThrownBy(() -> service.get(null, OrderReadScope.Global.INSTANCE))
        .isInstanceOf(OrderNotFoundException.class);
    assertThatThrownBy(() -> service.get(UUID.randomUUID(), OrderReadScope.None.INSTANCE))
        .isInstanceOf(OrderNotFoundException.class);
    verifyNoInteractions(orders);
  }
}
