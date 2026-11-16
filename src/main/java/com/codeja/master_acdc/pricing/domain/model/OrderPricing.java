package com.codeja.master_acdc.pricing.domain.model;

import com.codeja.master_acdc.pricing.domain.exception.InvalidPricingException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Total de un pedido: subtotal con descuento por volumen por línea, cupón (antes de impuestos), el
 * total nunca baja de cero, impuestos y redondeo a 2 decimales.
 */
public final class OrderPricing {

  private final Clock clock;
  private final BigDecimal taxRate;
  private final VolumeDiscount volumeDiscount = new VolumeDiscount();

  public OrderPricing(Clock clock, BigDecimal taxRate) {
    if (taxRate.signum() < 0) {
      throw new InvalidPricingException("tax rate must not be negative");
    }
    this.clock = clock;
    this.taxRate = taxRate;
  }

  public BigDecimal total(PricingOrder order) {
    return total(order, null);
  }

  public BigDecimal total(PricingOrder order, @Nullable Coupon coupon) {
    Money subtotal = subtotalOf(order);
    if (subtotal.amount().signum() == 0) {
      return Money.ZERO.amount();
    }
    Money taxable = applyCoupon(subtotal, coupon);
    return taxable.plus(taxable.times(taxRate)).amount();
  }

  public BigDecimal subtotal(PricingOrder order) {
    return subtotalOf(order).amount();
  }

  private Money subtotalOf(PricingOrder order) {
    Money subtotal = Money.ZERO;
    for (PricingLine line : order.lines()) {
      BigDecimal lineTotal = line.lineTotal();
      BigDecimal discount = volumeDiscount.of(line.units(), lineTotal);
      subtotal = subtotal.plus(new Money(lineTotal.subtract(discount)));
    }
    return subtotal;
  }

  private Money applyCoupon(Money subtotal, @Nullable Coupon coupon) {
    if (coupon == null || !coupon.isApplicableOn(LocalDate.now(clock))) {
      return subtotal;
    }
    Money discounted = subtotal.minus(new Money(coupon.discountOn(subtotal.amount())));
    if (discounted.isNegative()) {
      return Money.ZERO;
    }
    return discounted;
  }
}
