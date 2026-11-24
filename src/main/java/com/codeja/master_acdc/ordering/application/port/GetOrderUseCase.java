package com.codeja.master_acdc.ordering.application.port;

import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import java.util.UUID;

public interface GetOrderUseCase {
  OrderDetail get(UUID orderId, OrderReadScope scope);
}
