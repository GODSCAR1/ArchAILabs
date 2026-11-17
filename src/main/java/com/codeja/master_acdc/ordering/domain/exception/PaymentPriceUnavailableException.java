package com.codeja.master_acdc.ordering.domain.exception;

/** Indica que falta el precio de una línea del pedido. */
public class PaymentPriceUnavailableException extends RuntimeException {
  public PaymentPriceUnavailableException() {
    super("payment price unavailable");
  }
}
