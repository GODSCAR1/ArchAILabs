package com.codeja.master_acdc.ordering.application.port;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import java.util.UUID;

public interface GetOrderUseCase {
  Order get(UUID orderId, OrderReadScope scope);
}
