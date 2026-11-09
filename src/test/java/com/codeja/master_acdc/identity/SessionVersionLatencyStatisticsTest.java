package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionVersionLatencyStatisticsTest {
  @Test
  void should_report_nearest_rank_in_milliseconds_when_samples_are_unsorted() {
    long[] samples = {20_000_000, 1_000_000, 10_000_000, 5_000_000};
    assertThat(SessionVersionLatencyIT.percentile(samples, 0.50)).isEqualTo(5.0);
    assertThat(SessionVersionLatencyIT.percentile(samples, 0.95)).isEqualTo(20.0);
    assertThat(samples[0]).isEqualTo(20_000_000);
  }
}
