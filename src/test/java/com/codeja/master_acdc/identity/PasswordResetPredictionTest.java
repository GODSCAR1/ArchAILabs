package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.port.PasswordResetSecrets;
import com.codeja.master_acdc.identity.infrastructure.config.PasswordResetConfiguration;
import java.util.HexFormat;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordResetPredictionTest {
  @Test
  @DisplayName("El oráculo reconstruye la secuencia LCG conocida")
  void should_predict_next_bytes_when_random_has_known_seed() {
    Random random = new Random(123456789L);
    byte[] first = new byte[32];
    byte[] second = new byte[32];
    random.nextBytes(first);
    random.nextBytes(second);
    assertThat(PasswordResetLcgOracle.predictions(HexFormat.of().formatHex(first)))
        .contains(HexFormat.of().formatHex(second));
  }

  @Test
  @DisplayName("Token propio no permite predecir el siguiente token ajeno")
  void should_not_predict_next_secret_when_own_secret_is_observed() {
    PasswordResetSecrets secrets = new PasswordResetConfiguration().passwordResetSecrets();
    String own = secrets.generate();
    String other = secrets.generate();
    assertThat(PasswordResetLcgOracle.predictions(own).contains(other))
        .as("El token ajeno no debe coincidir con una predicción LCG")
        .isFalse();
  }
}
