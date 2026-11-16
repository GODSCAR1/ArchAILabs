package com.codeja.master_acdc.pricing.application;

import com.codeja.master_acdc.pricing.domain.exception.CouponAlreadyUsedException;
import com.codeja.master_acdc.pricing.domain.exception.CouponNotFoundException;
import com.codeja.master_acdc.pricing.domain.model.Coupon;
import com.codeja.master_acdc.pricing.domain.model.OrderPricing;
import com.codeja.master_acdc.pricing.domain.model.PricingOrder;
import com.codeja.master_acdc.pricing.domain.port.CouponRepository;
import java.math.BigDecimal;

/** Valora un pedido con un cupón y marca como usado el cupón de un solo uso. */
public final class CouponService {

  private final CouponRepository coupons;
  private final OrderPricing pricing;

  public CouponService(CouponRepository coupons, OrderPricing pricing) {
    this.coupons = coupons;
    this.pricing = pricing;
  }

  public BigDecimal checkout(PricingOrder order, String couponCode) {
    Coupon coupon =
        coupons.findByCode(couponCode).orElseThrow(() -> new CouponNotFoundException(couponCode));
    if (coupon.singleUse() && coupons.isUsed(couponCode)) {
      throw new CouponAlreadyUsedException(couponCode);
    }
    BigDecimal total = pricing.total(order, coupon);
    if (coupon.singleUse()) {
      coupons.markUsed(couponCode);
    }
    return total;
  }
}
