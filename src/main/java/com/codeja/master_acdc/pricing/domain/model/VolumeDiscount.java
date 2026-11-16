package com.codeja.master_acdc.pricing.domain.model;

import java.math.BigDecimal;

/** Descuento por volumen de una línea: 10 % desde 10 unidades (incluidas). */
public final class VolumeDiscount {

  static final int MIN_UNITS = 10;
  static final BigDecimal RATE = new BigDecimal("0.10");

  public BigDecimal of(int units, BigDecimal lineTotal) {
    if (units >= MIN_UNITS) {
      return new Money(lineTotal.multiply(RATE)).amount();
    }
    return Money.ZERO.amount();
  }
}
