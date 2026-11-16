package com.codeja.master_acdc.pricing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Importe con 2 decimales, redondeado con HALF_EVEN (redondeo bancario). */
public record Money(BigDecimal amount) {

  public static final int SCALE = 2;
  public static final Money ZERO = new Money(BigDecimal.ZERO);

  public Money {
    amount = round(amount);
  }

  public static Money of(String amount) {
    return new Money(new BigDecimal(amount));
  }

  static BigDecimal round(BigDecimal raw) {
    return raw.setScale(SCALE, RoundingMode.HALF_EVEN);
  }

  public Money plus(Money other) {
    return new Money(amount.add(other.amount));
  }

  public Money minus(Money other) {
    return new Money(amount.subtract(other.amount));
  }

  public Money times(BigDecimal factor) {
    return new Money(amount.multiply(factor));
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }
}
