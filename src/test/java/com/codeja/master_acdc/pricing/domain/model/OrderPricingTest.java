package com.codeja.master_acdc.pricing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.FixedClockExtension;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(FixedClockExtension.class)
class OrderPricingTest {

  private static final BigDecimal IVA = new BigDecimal("0.19");

  private OrderPricing pricing;
  // Sin IVA: las reglas del cupón (caducidad, suelo a cero) no dependen de "antes de impuestos".
  private OrderPricing pricingWithoutTax;
  private LocalDate today;

  @BeforeEach
  void setUp(Clock clock) {
    pricing = new OrderPricing(clock, IVA);
    pricingWithoutTax = new OrderPricing(clock, BigDecimal.ZERO);
    today = LocalDate.now(clock);
  }

  @Test
  @DisplayName("Una tasa de impuestos cero es válida y no incrementa el total")
  void should_leave_total_unchanged_when_tax_rate_is_zero(Clock clock) {
    OrderPricing withoutTax = new OrderPricing(clock, BigDecimal.ZERO);

    assertThat(withoutTax.total(orderWithSubtotal("100.00"))).isEqualByComparingTo("100.00");
  }

  @Test
  @DisplayName("Un cupón fijo puede superar 100 unidades monetarias")
  void should_apply_fixed_coupon_over_one_hundred_when_order_is_large_enough() {
    Coupon coupon = Coupon.fixed("FIXED", new BigDecimal("150"), today.plusDays(1));

    assertThat(pricing.total(orderWithSubtotal("200.00"), coupon)).isEqualByComparingTo("59.50");
  }

  @Test
  @DisplayName("El cupón se aplica antes de impuestos")
  void should_apply_coupon_before_tax_when_order_has_a_fixed_coupon() {
    Coupon tenOff = Coupon.fixed("TEN", new BigDecimal("10.00"), today.plusDays(1));

    // Antes de impuestos: (100,00 − 10,00) × 1,19 = 107,10. Después: 100,00 × 1,19 − 10,00 =
    // 109,00.
    // Con un cupón de porcentaje los dos órdenes dan lo mismo: la regla solo se ve con uno fijo.
    assertThat(pricing.total(orderWithSubtotal("100.00"), tenOff))
        .as("regla: el cupón se aplica antes de impuestos: (100,00 − 10,00) × 1,19")
        .isEqualByComparingTo("107.10");
  }

  @ParameterizedTest(name = "cupón válido hasta hoy {0} días → total {1}")
  @CsvSource({"1, 90.00", "0, 90.00", "-1, 100.00"})
  @DisplayName("Un cupón caducado no se aplica (vale hasta su fecha incluida)")
  void should_ignore_coupon_when_it_has_expired(int daysFromToday, BigDecimal expected) {
    Coupon tenOff = Coupon.fixed("TEN", new BigDecimal("10.00"), today.plusDays(daysFromToday));

    assertThat(pricingWithoutTax.total(orderWithSubtotal("100.00"), tenOff))
        .as(
            "regla: un cupón caducado no se aplica; vale hasta su fecha incluida (caduca hoy %+d"
                + " días)",
            daysFromToday)
        .isEqualByComparingTo(expected);
  }

  @ParameterizedTest(name = "subtotal {0} con cupón de {1} → total {2}")
  @CsvSource({"15.00, 50.00, 0.00", "15.00, 15.00, 0.00", "15.00, 14.99, 0.01"})
  @DisplayName("El total nunca es negativo")
  void should_never_go_below_zero_when_coupon_is_applied(
      String subtotal, BigDecimal couponAmount, BigDecimal expected) {
    Coupon coupon = Coupon.fixed("BIG", couponAmount, today.plusDays(1));

    assertThat(pricingWithoutTax.total(orderWithSubtotal(subtotal), coupon))
        .as("regla: el total nunca es negativo (subtotal %s, cupón %s)", subtotal, couponAmount)
        .isEqualByComparingTo(expected);
  }

  private static PricingOrder orderWithSubtotal(String amount) {
    return PricingOrder.of(new PricingLine("SKU-1", 1, new BigDecimal(amount)));
  }
}
