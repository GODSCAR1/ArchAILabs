package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import java.util.List;

public interface OrderSummaryQuery {

  List<OrderSummary> findByStatus(OrderStatus status);
}
