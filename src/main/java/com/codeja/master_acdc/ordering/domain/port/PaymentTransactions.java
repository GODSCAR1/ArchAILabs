package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import java.math.BigDecimal;
import java.util.UUID;

/** Operaciones transaccionales cortas alrededor de la pasarela. */
public interface PaymentTransactions {
  PaymentAttempt begin(UUID orderId, UUID customerId, BigDecimal amount);

  void failed(UUID orderId, UUID customerId, boolean declined);

  Order complete(Order order);
}
