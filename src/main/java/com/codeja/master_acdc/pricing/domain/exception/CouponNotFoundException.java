package com.codeja.master_acdc.pricing.domain.exception;

/** Señala que no existe un cupón con ese código. */
public class CouponNotFoundException extends RuntimeException {

  public CouponNotFoundException(String code) {
    super("coupon not found: " + code);
  }
}
