package com.codeja.master_acdc.catalog.domain.model;

import java.math.BigDecimal;

public record ProductRating(BigDecimal average, long count) {

  public ProductRating {
    if (average == null || count < 0) {
      throw new IllegalArgumentException("rating values are invalid");
    }
  }

  public static ProductRating empty() {
    return new ProductRating(BigDecimal.ZERO.setScale(2), 0);
  }
}
