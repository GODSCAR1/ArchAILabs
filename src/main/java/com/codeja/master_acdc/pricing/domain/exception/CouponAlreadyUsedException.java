package com.codeja.master_acdc.pricing.domain.exception;

/** Señala que un cupón de un solo uso ya se usó. */
public class CouponAlreadyUsedException extends RuntimeException {

  public CouponAlreadyUsedException(String code) {
    super("coupon already used: " + code);
  }
}
