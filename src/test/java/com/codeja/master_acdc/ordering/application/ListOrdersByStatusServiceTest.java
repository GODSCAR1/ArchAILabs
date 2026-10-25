package com.codeja.master_acdc.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ListOrdersByStatusServiceTest {

  @Test
  void should_delegate_status_and_return_summaries_when_listing_orders() {
    OrderSummaryQuery query = Mockito.mock(OrderSummaryQuery.class);
    OrderSummary summary = new OrderSummary(UUID.randomUUID(), "Ada", 2, 5, List.of());
    given(query.findByStatus(OrderStatus.PAID)).willReturn(List.of(summary));

    List<OrderSummary> result = new ListOrdersByStatusService(query).list(OrderStatus.PAID);

    assertThat(result).containsExactly(summary);
    verify(query).findByStatus(OrderStatus.PAID);
  }
}
