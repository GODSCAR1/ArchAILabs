package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecurePasswordResetSecretGeneratorTest {
  @Test
  void should_generate_hex_and_consistent_hash_when_creating_secret() {
    var generator = new SecurePasswordResetSecretGenerator();
    String token = generator.generate();
    assertThat(token).matches("[0-9a-f]{64}");
    assertThat(generator.hash(token)).matches("[0-9a-f]{64}").isEqualTo(generator.hash(token));
  }
}
