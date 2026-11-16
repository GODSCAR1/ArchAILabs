package com.codeja.master_acdc.pricing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({"1.005, 1.00", "1.015, 1.02", "1.025, 1.02", "-1.005, -1.00", "2.0049, 2.00"})
  @DisplayName("Los importes se redondean a 2 decimales con HALF_EVEN (redondeo bancario)")
  void should_round_to_two_decimals_half_even_when_amount_is_created(String raw, String expected) {
    // En el empate (tercer decimal 5 exacto) se redondea al par: 1,005 → 1,00 y 1,015 → 1,02.
    // Con HALF_UP serían 1,01 y 1,02; con HALF_DOWN, 1,00 y 1,01.
    assertThat(Money.of(raw).amount())
        .as("regla: redondeo a 2 decimales HALF_EVEN; %s debe quedar en %s", raw, expected)
        .isEqualByComparingTo(expected);
  }
}
