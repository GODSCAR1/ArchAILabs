package com.codeja.master_acdc.pricing.domain.port;

import com.codeja.master_acdc.pricing.domain.model.Coupon;
import java.util.Optional;

/** Cupones y su uso. */
public interface CouponRepository {

  Optional<Coupon> findByCode(String code);

  boolean isUsed(String code);

  void markUsed(String code);
}
