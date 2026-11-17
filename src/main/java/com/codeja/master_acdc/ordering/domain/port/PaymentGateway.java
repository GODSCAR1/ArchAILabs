package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.model.PaymentResult;
import java.util.UUID;

/** Autoriza un intento sin repetir automáticamente la petición. */
public interface PaymentGateway {
  PaymentResult authorize(UUID orderId, PaymentAttempt attempt);
}
