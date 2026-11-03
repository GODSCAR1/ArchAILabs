package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Generates randomized expirations to spread cache refreshes. */
public final class ProductCacheTtl {

  private ProductCacheTtl() {}

  public static Duration jitter(Duration base) {
    long baseNanos = base.toNanos();
    long delta = baseNanos / 10;
    return Duration.ofNanos(
        ThreadLocalRandom.current().nextLong(baseNanos - delta, baseNanos + delta + 1));
  }
}
