package com.codeja.master_acdc.pricing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.pricing.domain.exception.InvalidPricingException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PricingTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
  private final OrderPricing pricing =
      new OrderPricing(
          Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC),
          new BigDecimal("0.21"));

  @Test
  @DisplayName("El descuento por volumen se aplica por línea antes del cupón y los impuestos")
  void should_apply_volume_coupon_and_tax_when_calculating_total() {
    PricingOrder order = PricingOrder.of(line(9, "10"), line(10, "10"));
    Coupon coupon = Coupon.percent("SALE", new BigDecimal("10"), TODAY);

    assertThat(pricing.subtotal(order)).isEqualByComparingTo("180.00");
    assertThat(pricing.total(order, coupon)).isEqualByComparingTo("196.02");
    assertThat(pricing.total(order)).isEqualByComparingTo("217.80");
  }

  @Test
  @DisplayName("El cupón caducado no se aplica y el vigente nunca reduce el total bajo cero")
  void should_respect_expiry_and_zero_floor_when_applying_fixed_coupon() {
    PricingOrder order = PricingOrder.of(line(1, "10"));
    Coupon expired = Coupon.fixed("OLD", new BigDecimal("20"), TODAY.minusDays(1));
    Coupon valid = Coupon.fixed("NOW", new BigDecimal("20"), TODAY);

    assertThat(pricing.total(order, expired)).isEqualByComparingTo("12.10");
    assertThat(pricing.total(order, valid)).isEqualByComparingTo("0.00");
    assertThat(pricing.total(PricingOrder.of(line(1, "0")), valid)).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("Los importes usan redondeo bancario y operaciones inmutables")
  void should_round_half_even_when_operating_on_money() {
    assertThat(Money.of("1.005").amount()).isEqualByComparingTo("1.00");
    assertThat(Money.of("1.015").amount()).isEqualByComparingTo("1.02");
    assertThat(Money.of("2").minus(Money.of("3")).isNegative()).isTrue();
    assertThat(Money.of("2").times(new BigDecimal("1.5")).plus(Money.of("1")).amount())
        .isEqualByComparingTo("4.00");
  }

  @Test
  @DisplayName("El pedido conserva una copia inmutable de sus líneas")
  void should_keep_lines_immutable_when_input_list_changes() {
    List<PricingLine> lines = new ArrayList<>(List.of(line(1, "10")));
    PricingOrder order = new PricingOrder(lines);
    lines.clear();

    assertThat(order.lines()).hasSize(1);
    assertThatThrownBy(() -> order.lines().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("Las líneas, pedidos y tasas inválidos se rechazan")
  void should_reject_invalid_order_inputs_when_constructed() {
    assertThatThrownBy(() -> line(0, "1"))
        .isInstanceOf(InvalidPricingException.class)
        .hasMessage("units must be greater than 0");
    assertThatThrownBy(() -> line(1, "-1"))
        .isInstanceOf(InvalidPricingException.class)
        .hasMessage("unit price must not be negative");
    assertThatThrownBy(() -> PricingOrder.of())
        .isInstanceOf(InvalidPricingException.class)
        .hasMessage("order must have at least one line");
    assertThatThrownBy(() -> new OrderPricing(Clock.systemUTC(), new BigDecimal("-0.01")))
        .isInstanceOf(InvalidPricingException.class)
        .hasMessage("tax rate must not be negative");
  }

  @Test
  @DisplayName("Los cupones validan código, valor y límite porcentual")
  void should_reject_invalid_coupons_when_constructed() {
    assertThatThrownBy(() -> Coupon.fixed(" ", BigDecimal.ONE, TODAY))
        .isInstanceOf(InvalidPricingException.class)
        .hasMessage("coupon code must not be blank");
    assertThatThrownBy(() -> Coupon.fixed("X", BigDecimal.ZERO, TODAY))
        .isInstanceOf(InvalidPricingException.class)
        .hasMessage("coupon value must be greater than 0");
    assertThatThrownBy(() -> Coupon.percent("X", new BigDecimal("101"), TODAY))
        .isInstanceOf(InvalidPricingException.class)
        .hasMessage("percent coupon must not exceed 100");
    assertThat(Coupon.percent("X", new BigDecimal("100"), TODAY).discountOn(BigDecimal.TEN))
        .isEqualByComparingTo("10");
  }

  private static PricingLine line(int units, String price) {
    return new PricingLine("SKU", units, new BigDecimal(price));
  }
}
