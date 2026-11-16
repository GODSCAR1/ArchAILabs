package com.codeja.master_acdc.pricing.domain.model;

import com.codeja.master_acdc.pricing.domain.exception.InvalidPricingException;
import java.util.List;

/** Pedido a valorar: al menos una línea. */
public record PricingOrder(List<PricingLine> lines) {

  public PricingOrder {
    if (lines.isEmpty()) {
      throw new InvalidPricingException("order must have at least one line");
    }
    lines = List.copyOf(lines);
  }

  public static PricingOrder of(PricingLine... lines) {
    return new PricingOrder(List.of(lines));
  }
}
