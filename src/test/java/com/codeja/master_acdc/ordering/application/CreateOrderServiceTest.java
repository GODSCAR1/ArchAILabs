package com.codeja.master_acdc.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.codeja.master_acdc.ordering.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.ordering.domain.exception.UnknownProductException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.ReservationOutcome;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.StockReserver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateOrderServiceTest {

  private static final UUID CUSTOMER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final StockReserver stockReserver = mock(StockReserver.class);
  private final CreateOrderService service = new CreateOrderService(orderRepository, stockReserver);

  @Test
  void should_create_pending_order_when_reservation_succeeds() {
    given(stockReserver.reserve(List.of(new OrderLine(A, 3), new OrderLine(B, 2))))
        .willReturn(ReservationOutcome.RESERVED);

    Order order = service.create(CUSTOMER, List.of(new OrderLine(B, 2), new OrderLine(A, 3)));

    assertThat(order.customerId()).isEqualTo(CUSTOMER);
    assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.lines()).containsExactly(new OrderLine(B, 2), new OrderLine(A, 3));
    then(orderRepository).should().save(order);
  }

  @Test
  void should_report_insufficient_stock_when_reservation_rejected() {
    given(stockReserver.reserve(any())).willReturn(ReservationOutcome.INSUFFICIENT_STOCK);

    assertThatThrownBy(() -> service.create(CUSTOMER, List.of(new OrderLine(A, 3))))
        .isInstanceOf(InsufficientStockException.class);

    then(orderRepository).should(never()).save(any());
  }

  @Test
  void should_report_unknown_product_when_reservation_reports_unknown() {
    given(stockReserver.reserve(any())).willReturn(ReservationOutcome.UNKNOWN_PRODUCT);

    assertThatThrownBy(() -> service.create(CUSTOMER, List.of(new OrderLine(A, 3))))
        .isInstanceOf(UnknownProductException.class);

    then(orderRepository).should(never()).save(any());
  }
}
