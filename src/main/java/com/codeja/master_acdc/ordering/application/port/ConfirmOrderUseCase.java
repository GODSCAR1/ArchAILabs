package com.codeja.master_acdc.ordering.application.port;

import com.codeja.master_acdc.ordering.domain.model.Order;
import java.util.UUID;

public interface ConfirmOrderUseCase {
  Order confirm(UUID orderId, UUID customerId);
}
