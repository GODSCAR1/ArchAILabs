package com.codeja.master_acdc.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SessionVersionTest {
  @Test
  void should_increment_exactly_when_version_has_capacity() {
    assertThat(new SessionVersion(0).next()).isEqualTo(new SessionVersion(1));
    assertThatThrownBy(() -> new SessionVersion(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SessionVersion(Long.MAX_VALUE).next())
        .hasMessage("session-version-unavailable");
  }
}
