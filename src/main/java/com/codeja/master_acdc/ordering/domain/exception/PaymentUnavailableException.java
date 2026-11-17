package com.codeja.master_acdc.ordering.domain.exception;

/** La pasarela no devolvió un resultado definitivo. */
public class PaymentUnavailableException extends RuntimeException {
  public PaymentUnavailableException(Throwable cause) {
    super("payment gateway unavailable", cause);
  }
}
