package com.codeja.master_acdc.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.LoginRequest;
import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.PasswordChangeRequest;
import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.PasswordResetConfirmBody;
import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.PasswordResetRequestBody;
import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.RegisterRequest;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IdentityRedactionTest {
  private static final String SECRET = "sensitive-value-4823";
  private static final String EMAIL = "user@example.com";

  @ParameterizedTest
  @MethodSource("sensitiveValues")
  @DisplayName("Representaciones no revelan secretos ni datos de recuperación")
  void should_hide_sensitive_values_when_converted_to_string(Object value) {
    assertThat(value.toString()).doesNotContain(SECRET, EMAIL);
  }

  private static Stream<Object> sensitiveValues() {
    var access = new AccessToken(SECRET, 1);
    var login = new LoginResult(access, SECRET, Instant.EPOCH);
    return Stream.of(
        login,
        access,
        new PasswordResetToken(UUID.randomUUID(), SECRET, Instant.EPOCH),
        new PasswordResetRequest(EMAIL),
        new PasswordResetConfirmation(EMAIL, SECRET, SECRET),
        new Registration(EMAIL, SECRET, SECRET),
        new PasswordResetMessage(EMAIL, SECRET),
        RefreshResult.issued(login),
        new LoginRequest(EMAIL, SECRET),
        new PasswordChangeRequest(SECRET, SECRET),
        new PasswordResetConfirmBody(EMAIL, SECRET, SECRET),
        new PasswordResetRequestBody(EMAIL),
        new RegisterRequest(EMAIL, SECRET, SECRET));
  }
}
