package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SecureRefreshSecretGeneratorTest {
  @Test
  void should_generate_opaque_secret_and_sha256_when_new_refresh_is_requested() throws Exception {
    var generator = new SecureRefreshSecretGenerator();
    var first = generator.generate();
    var second = generator.generate();
    assertThat(first.value()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second.value());
    assertThat(Base64.getUrlDecoder().decode(first.value())).hasSize(32);
    assertThat(first.hash())
        .isEqualTo(
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(first.value().getBytes(StandardCharsets.US_ASCII))));
    assertThat(generator.hash(first.value())).isEqualTo(first.hash());
    assertThat(first.toString()).doesNotContain(first.value(), first.hash());
  }
}
