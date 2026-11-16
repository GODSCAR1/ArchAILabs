package com.codeja.master_acdc.pricing.domain.model;

import com.codeja.master_acdc.pricing.domain.exception.InvalidPricingException;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cupón de descuento: importe fijo o porcentaje sobre el subtotal. Vale hasta {@code validUntil}
 * incluido; un cupón de un solo uso se marca como usado al aplicarse.
 */
public record Coupon(
    String code, Kind kind, BigDecimal value, LocalDate validUntil, boolean singleUse) {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  /** Tipo de cupón. */
  public enum Kind {
    FIXED,
    PERCENT
  }

  public Coupon {
    if (code.isBlank()) {
      throw new InvalidPricingException("coupon code must not be blank");
    }
    if (value.signum() <= 0) {
      throw new InvalidPricingException("coupon value must be greater than 0");
    }
    if (kind == Kind.PERCENT && value.compareTo(HUNDRED) > 0) {
      throw new InvalidPricingException("percent coupon must not exceed 100");
    }
  }

  public static Coupon fixed(String code, BigDecimal amount, LocalDate validUntil) {
    return new Coupon(code, Kind.FIXED, amount, validUntil, false);
  }

  public static Coupon percent(String code, BigDecimal percent, LocalDate validUntil) {
    return new Coupon(code, Kind.PERCENT, percent, validUntil, false);
  }

  public Coupon asSingleUse() {
    return new Coupon(code, kind, value, validUntil, true);
  }

  public boolean isApplicableOn(LocalDate today) {
    return !today.isAfter(validUntil);
  }

  public BigDecimal discountOn(BigDecimal subtotal) {
    return switch (kind) {
      case FIXED -> value;
      case PERCENT -> subtotal.multiply(value).divide(HUNDRED);
    };
  }
}
