package com.codeja.master_acdc.pricing.domain.exception;

/** Señala datos de precio no válidos (línea, pedido, cupón o impuesto). */
public class InvalidPricingException extends RuntimeException {

  public InvalidPricingException(String message) {
    super(message);
  }
}
