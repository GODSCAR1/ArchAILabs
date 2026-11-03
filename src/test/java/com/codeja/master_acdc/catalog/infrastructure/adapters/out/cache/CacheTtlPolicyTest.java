package com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CacheTtlPolicyTest {

  @Test
  @DisplayName("El TTL máximo L1 sigue bajo treinta segundos")
  void should_keep_maximum_l1_ttl_below_thirty_seconds_when_jitter_is_applied() {
    Duration maximumL1 =
        Duration.ofNanos(
            (long) (CacheTtlPolicy.PRODUCTS_L1_TTL.toNanos() * (1 + CacheTtlPolicy.JITTER)));

    assertThat(maximumL1).isLessThan(Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("L2 dura más que L1")
  void should_keep_l2_ttl_longer_than_l1_when_policy_is_defined() {
    assertThat(CacheTtlPolicy.PRODUCTS_L2_TTL).isGreaterThan(CacheTtlPolicy.PRODUCTS_L1_TTL);
  }

  @Test
  @DisplayName("Mil muestras de jitter quedan en el rango de diez por ciento")
  void should_jitter_one_thousand_samples_within_ten_percent_when_ttl_is_generated() {
    assertJitterWithinRange(CacheTtlPolicy.PRODUCTS_L1_TTL);
    assertJitterWithinRange(CacheTtlPolicy.PRODUCTS_L2_TTL);
  }

  private static void assertJitterWithinRange(Duration base) {
    Duration minimum = base.multipliedBy(9).dividedBy(10);
    Duration maximum = base.multipliedBy(11).dividedBy(10);
    List<Duration> samples =
        IntStream.range(0, 1_000).mapToObj(index -> CacheTtlPolicy.jitter(base)).toList();

    assertThat(samples)
        .allSatisfy(
            sample -> {
              assertThat(sample).isGreaterThanOrEqualTo(minimum);
              assertThat(sample).isLessThanOrEqualTo(maximum);
            });
    assertThat(new HashSet<>(samples)).hasSizeGreaterThan(1);
  }
}
