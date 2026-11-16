package com.codeja.master_acdc.pricing.domain.model;

import com.codeja.master_acdc.pricing.domain.exception.InvalidPricingException;
import java.math.BigDecimal;

/** Línea a valorar: unidades y precio unitario. */
public record PricingLine(String sku, int units, BigDecimal unitPrice) {

  public PricingLine {
    if (units <= 0) {
      throw new InvalidPricingException("units must be greater than 0");
    }
    if (unitPrice.signum() < 0) {
      throw new InvalidPricingException("unit price must not be negative");
    }
  }

  public BigDecimal lineTotal() {
    return unitPrice.multiply(BigDecimal.valueOf(units));
  }
}
