package com.codeja.master_acdc.pricing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VolumeDiscountTest {

  @ParameterizedTest(name = "{0} unidades → descuento {2}")
  @CsvSource({"9, 90.00, 0.00", "10, 100.00, 10.00", "11, 110.00, 11.00"})
  @DisplayName("10 por ciento de descuento desde 10 unidades (incluidas)")
  void should_discount_ten_percent_when_line_reaches_ten_units(
      int units, BigDecimal lineTotal, BigDecimal expected) {
    assertThat(new VolumeDiscount().of(units, lineTotal))
        .as("regla: 10 por ciento de descuento desde 10 unidades (incluidas); %d unidades", units)
        .isEqualByComparingTo(expected);
  }
}
