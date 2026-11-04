package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Shared TTL values and jitter policy for the products cache tiers. */
public final class CacheTtlPolicy {

  public static final Duration PRODUCTS_L1_TTL = Duration.ofSeconds(15);
  public static final Duration PRODUCTS_L2_TTL = Duration.ofMinutes(10);
  public static final double JITTER = 0.10;

  private CacheTtlPolicy() {}

  public static Duration jitter(Duration base) {
    long baseNanos = base.toNanos();
    long delta = (long) (baseNanos * JITTER);
    return Duration.ofNanos(
        ThreadLocalRandom.current().nextLong(baseNanos - delta, baseNanos + delta + 1));
  }
}
