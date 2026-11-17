package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Transiciones condicionales del pago, ejecutadas en una transacción del llamador. */
public interface OrderPaymentRepository {
  Optional<BigDecimal> findAmount(UUID orderId, UUID customerId);

  Optional<PaymentAttempt> start(UUID orderId, UUID customerId, BigDecimal amount);

  boolean returnToPending(UUID orderId, UUID customerId, boolean declined);

  boolean markPaid(UUID orderId, UUID customerId);
}
