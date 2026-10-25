package com.codeja.master_acdc.ordering.application;

import com.codeja.master_acdc.ordering.application.port.ListOrdersByStatusUseCase;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import java.util.List;

public class ListOrdersByStatusService implements ListOrdersByStatusUseCase {

  private final OrderSummaryQuery orderSummaryQuery;

  public ListOrdersByStatusService(OrderSummaryQuery orderSummaryQuery) {
    this.orderSummaryQuery = orderSummaryQuery;
  }

  @Override
  public List<OrderSummary> list(OrderStatus status) {
    return orderSummaryQuery.findByStatus(status);
  }
}
