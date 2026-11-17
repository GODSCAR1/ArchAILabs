package com.codeja.master_acdc.ordering.domain.exception;

/** La pasarela no respondió dentro del tiempo configurado. */
public class PaymentTimeoutException extends RuntimeException {
  public PaymentTimeoutException(Throwable cause) {
    super("payment gateway timed out", cause);
  }
}
