package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface OrderPayments {
  Optional<BigDecimal> fixedAmount(UUID orderId, UUID customerId);

  PaymentAttempt startAttempt(UUID orderId, UUID customerId, BigDecimal amount);

  void abandonAttempt(UUID orderId, UUID customerId, boolean declined);

  void settle(Order order);
}
