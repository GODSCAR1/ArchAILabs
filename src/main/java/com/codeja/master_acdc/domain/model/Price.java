package com.codeja.master_acdc.domain.model;

import com.codeja.master_acdc.domain.exception.InvalidProductException;
import java.math.BigDecimal;

/** Precio en pesos colombianos (COP): entero y mayor que cero. */
public record Price(long value) {

  public Price {
    if (value <= 0) {
      throw new InvalidProductException("price must be greater than 0");
    }
  }

  public static Price of(BigDecimal raw) {
    if (raw == null) {
      throw new InvalidProductException("price must not be null");
    }
    if (raw.stripTrailingZeros().scale() > 0) {
      throw new InvalidProductException("price must not have decimals");
    }
    try {
      return new Price(raw.longValueExact());
    } catch (ArithmeticException outOfRange) {
      throw new InvalidProductException("price is out of range");
    }
  }
}
