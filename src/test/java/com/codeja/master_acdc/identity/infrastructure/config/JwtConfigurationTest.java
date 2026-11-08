package com.codeja.master_acdc.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtSettings;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.PemKeyLoader;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtConfigurationTest {
  @TempDir Path directory;

  @Test
  void should_reuse_pem_when_independent_spring_contexts_start() {
    try (var first =
            new SpringApplicationBuilder(JwtConfiguration.class)
                .web(WebApplicationType.NONE)
                .run();
        var second =
            new SpringApplicationBuilder(JwtConfiguration.class)
                .web(WebApplicationType.NONE)
                .run()) {
      var settings = first.getBean(JwtSettings.class);
      assertThat(settings).isEqualTo(second.getBean(JwtSettings.class));
      assertThat(settings.issuer()).isEqualTo("https://api.master-acdc.dev");
      assertThat(settings.audience()).isEqualTo("master-acdc-api");
      var key = first.getBean(RSAKey.class);
      assertThat(key.getKeyID()).isEqualTo(key.toPublicJWK().computeThumbprint().toString());
      assertThat(second.getBean(RSAKey.class).getKeyID()).isEqualTo(key.getKeyID());
      assertThat(first.getBean(JwtEncoder.class)).isNotNull();
      assertThat(first.getBean(JwtDecoder.class)).isNotNull();
    } catch (com.nimbusds.jose.JOSEException exception) {
      throw new AssertionError(exception);
    }
  }

  @Test
  void should_reject_key_when_pem_is_absent_or_invalid() throws java.io.IOException {
    Path invalid = directory.resolve("invalid.pem");
    Files.writeString(invalid, "invalid");
    assertThatThrownBy(() -> PemKeyLoader.load(invalid.toString()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PemKeyLoader.load(directory.resolve("missing.pem").toString()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwtSettings("", "https://api.master-acdc.dev", "master-acdc-api"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwtSettings("key", "not-absolute", "master-acdc-api"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwtSettings("key", "https://api.master-acdc.dev", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
