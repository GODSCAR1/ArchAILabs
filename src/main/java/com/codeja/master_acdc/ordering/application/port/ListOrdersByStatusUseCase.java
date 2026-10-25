package com.codeja.master_acdc.ordering.application.port;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import java.util.List;

public interface ListOrdersByStatusUseCase {

  List<OrderSummary> list(OrderStatus status);
}
