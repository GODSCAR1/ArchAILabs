package com.codeja.master_acdc.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.FixedClockExtension;
import com.codeja.master_acdc.pricing.domain.exception.CouponAlreadyUsedException;
import com.codeja.master_acdc.pricing.domain.exception.CouponNotFoundException;
import com.codeja.master_acdc.pricing.domain.model.Coupon;
import com.codeja.master_acdc.pricing.domain.model.OrderPricing;
import com.codeja.master_acdc.pricing.domain.model.PricingLine;
import com.codeja.master_acdc.pricing.domain.model.PricingOrder;
import com.codeja.master_acdc.pricing.domain.port.CouponRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixedClockExtension.class)
class CouponServiceTest {
  private final InMemoryCoupons coupons = new InMemoryCoupons();
  private final PricingOrder order =
      PricingOrder.of(new PricingLine("SKU", 1, new BigDecimal("100")));
  private CouponService service;
  private LocalDate today;

  @BeforeEach
  void setUp(Clock clock) {
    service = new CouponService(coupons, new OrderPricing(clock, new BigDecimal("0.19")));
    today = LocalDate.now(clock);
  }

  @Test
  @DisplayName("Un cupón inexistente se rechaza")
  void should_reject_checkout_when_coupon_does_not_exist() {
    assertThatThrownBy(() -> service.checkout(order, "MISSING"))
        .isInstanceOf(CouponNotFoundException.class);
  }

  @Test
  @DisplayName("Un cupón de un solo uso se aplica una vez y se rechaza al reutilizarlo")
  void should_reject_second_checkout_when_coupon_is_single_use() {
    coupons.add(Coupon.fixed("ONCE", BigDecimal.TEN, today).asSingleUse());

    assertThat(service.checkout(order, "ONCE")).isEqualByComparingTo("107.10");
    assertThatThrownBy(() -> service.checkout(order, "ONCE"))
        .isInstanceOf(CouponAlreadyUsedException.class);
  }

  @Test
  @DisplayName("Un cupón reutilizable se aplica aunque conste como usado")
  void should_apply_coupon_again_when_coupon_is_not_single_use() {
    coupons.add(Coupon.fixed("REUSE", BigDecimal.TEN, today));
    coupons.markUsed("REUSE");

    assertThat(service.checkout(order, "REUSE")).isEqualByComparingTo("107.10");
    assertThat(service.checkout(order, "REUSE")).isEqualByComparingTo("107.10");
  }

  private static final class InMemoryCoupons implements CouponRepository {
    private final Map<String, Coupon> values = new HashMap<>();
    private final Set<String> used = new HashSet<>();

    void add(Coupon coupon) {
      values.put(coupon.code(), coupon);
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
      return Optional.ofNullable(values.get(code));
    }

    @Override
    public boolean isUsed(String code) {
      return used.contains(code);
    }

    @Override
    public void markUsed(String code) {
      used.add(code);
    }
  }
}
