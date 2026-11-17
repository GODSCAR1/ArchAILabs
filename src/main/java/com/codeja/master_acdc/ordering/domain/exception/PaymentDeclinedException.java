package com.codeja.master_acdc.ordering.domain.exception;

public class PaymentDeclinedException extends RuntimeException {
  public PaymentDeclinedException() {
    super("payment declined");
  }
}
