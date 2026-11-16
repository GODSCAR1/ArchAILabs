package com.codeja.master_acdc.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.pricing.domain.exception.CouponAlreadyUsedException;
import com.codeja.master_acdc.pricing.domain.exception.CouponNotFoundException;
import com.codeja.master_acdc.pricing.domain.model.Coupon;
import com.codeja.master_acdc.pricing.domain.model.OrderPricing;
import com.codeja.master_acdc.pricing.domain.model.PricingLine;
import com.codeja.master_acdc.pricing.domain.model.PricingOrder;
import com.codeja.master_acdc.pricing.domain.port.CouponRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CouponServiceTest {
  private final CouponRepository repository = mock(CouponRepository.class);
  private final OrderPricing pricing =
      new OrderPricing(
          Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC), BigDecimal.ZERO);
  private final CouponService service = new CouponService(repository, pricing);
  private final PricingOrder order = PricingOrder.of(new PricingLine("SKU", 1, BigDecimal.TEN));
  private final Coupon coupon =
      Coupon.fixed("SALE", new BigDecimal("2"), LocalDate.of(2026, 1, 15));

  @Test
  @DisplayName("Un cupón reutilizable descuenta sin consultar ni registrar su uso")
  void should_discount_without_marking_when_coupon_is_reusable() {
    when(repository.findByCode("SALE")).thenReturn(Optional.of(coupon));

    assertThat(service.checkout(order, "SALE")).isEqualByComparingTo("8.00");
    verify(repository, never()).isUsed("SALE");
    verify(repository, never()).markUsed("SALE");
  }

  @Test
  @DisplayName("Un cupón de un solo uso se registra después del cálculo")
  void should_mark_coupon_used_when_single_use_coupon_is_available() {
    when(repository.findByCode("SALE")).thenReturn(Optional.of(coupon.asSingleUse()));

    assertThat(service.checkout(order, "SALE")).isEqualByComparingTo("8.00");
    verify(repository).markUsed("SALE");
  }

  @Test
  @DisplayName("Un cupón de un solo uso ya consumido se rechaza")
  void should_reject_coupon_when_single_use_coupon_was_used() {
    when(repository.findByCode("SALE")).thenReturn(Optional.of(coupon.asSingleUse()));
    when(repository.isUsed("SALE")).thenReturn(true);

    assertThatThrownBy(() -> service.checkout(order, "SALE"))
        .isInstanceOf(CouponAlreadyUsedException.class)
        .hasMessage("coupon already used: SALE");
    verify(repository, never()).markUsed("SALE");
  }

  @Test
  @DisplayName("Un cupón inexistente se rechaza")
  void should_reject_checkout_when_coupon_is_missing() {
    assertThatThrownBy(() -> service.checkout(order, "MISSING"))
        .isInstanceOf(CouponNotFoundException.class)
        .hasMessage("coupon not found: MISSING");
    verify(repository, never()).markUsed("MISSING");
  }
}
