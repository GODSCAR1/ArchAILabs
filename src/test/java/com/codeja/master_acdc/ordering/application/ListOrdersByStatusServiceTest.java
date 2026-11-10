package com.codeja.master_acdc.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codeja.master_acdc.ordering.domain.model.OrderListingCriteria;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
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
    OrderSummary summary = new OrderSummary(UUID.randomUUID(), "Ada", 2, 5);
    var criteria =
        new OrderListingCriteria(
            OrderStatus.PAID, new OrderReadScope.Own(UUID.randomUUID()), "totalUnits DESC");
    given(query.findByStatus(criteria)).willReturn(List.of(summary));

    List<OrderSummary> result = new ListOrdersByStatusService(query).list(criteria);

    assertThat(result).containsExactly(summary);
    verify(query).findByStatus(criteria);
  }

  @Test
  void should_skip_query_when_account_has_no_customer_or_global_scope() {
    var query = Mockito.mock(OrderSummaryQuery.class);
    assertThat(
            new ListOrdersByStatusService(query)
                .list(
                    new OrderListingCriteria(
                        OrderStatus.PAID, OrderReadScope.None.INSTANCE, "id ASC")))
        .isEmpty();
    verifyNoInteractions(query);
  }
}
