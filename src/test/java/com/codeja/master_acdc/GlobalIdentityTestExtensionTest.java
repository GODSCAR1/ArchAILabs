package com.codeja.master_acdc;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtSettings;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.PemKeyLoader;
import com.codeja.master_acdc.identity.infrastructure.config.JwtConfiguration;
import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.Extension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(JwtConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalIdentityTestExtensionTest {
  private final String pathBeforePostProcessing =
      Objects.requireNonNull(System.getProperty("shop.jwt.private-key"));
  @Autowired private JwtSettings settings;

  @Test
  void should_initialize_before_spring_when_test_instance_is_per_class() {
    assertThat(settings.privateKey()).isEqualTo(pathBeforePostProcessing);
    assertThat(Path.of(pathBeforePostProcessing)).exists();
    assertThat(
            Path.of(pathBeforePostProcessing)
                .toAbsolutePath()
                .startsWith(Path.of("").toAbsolutePath()))
        .isFalse();
    assertThat(PemKeyLoader.load(settings.privateKey()).size()).isEqualTo(2048);
    assertThat(System.getProperty("shop.ops.password-hash")).startsWith("{bcrypt}");
    assertThat(ServiceLoader.load(Extension.class).stream().map(ServiceLoader.Provider::type))
        .contains(GlobalIdentityTestExtension.class);
  }

  @Test
  void should_reuse_key_when_extension_is_instantiated_again() {
    String kid = PemKeyLoader.load(pathBeforePostProcessing).getKeyID();
    new GlobalIdentityTestExtension();
    new GlobalIdentityTestExtension();
    assertThat(System.getProperty("shop.jwt.private-key")).isEqualTo(pathBeforePostProcessing);
    assertThat(PemKeyLoader.load(pathBeforePostProcessing).getKeyID()).isEqualTo(kid);
  }
}
