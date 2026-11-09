package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SessionVersionValidatorTest {
  private final SessionVersionValidator validator =
      new SessionVersionValidator(id -> new SessionVersion(7));

  @Test
  @DisplayName("Token sin versión no autentica")
  void should_reject_missing_claim_when_other_claims_are_valid() {
    assertThat(validator.validate(token(null)).hasErrors()).isTrue();
  }

  @Test
  @DisplayName("Versión no numérica no autentica")
  void should_reject_non_integral_or_invalid_values_when_version_is_untrusted() {
    for (Object value :
        List.of(
            "7",
            "text",
            true,
            Map.of("v", 7),
            List.of(7),
            -1,
            new BigDecimal("7.5"),
            new BigInteger("9223372036854775808"),
            Double.NaN,
            7.0d,
            7.0f)) {
      assertThat(validator.validate(token(value)).hasErrors()).as("%s", value).isTrue();
    }
  }

  @Test
  @DisplayName("Versión distinta o cuenta inexistente no autentica")
  void should_require_exact_equality_when_shared_version_is_available() {
    assertThat(validator.validate(token(7L)).hasErrors()).isFalse();
    assertThat(validator.validate(token(6L)).hasErrors()).isTrue();
    assertThat(validator.validate(token(8L)).hasErrors()).isTrue();
    var unavailable =
        new SessionVersionValidator(
            id -> {
              throw new IdentityException("session-account-absent");
            });
    assertThat(unavailable.validate(token(7)).hasErrors()).isTrue();
    var maximum = new SessionVersionValidator(id -> new SessionVersion(Long.MAX_VALUE));
    assertThat(maximum.validate(token(Long.MAX_VALUE)).hasErrors()).isFalse();
    assertThat(maximum.validate(token(Long.MAX_VALUE - 1)).hasErrors()).isTrue();
  }

  private static Jwt token(Object version) {
    var builder =
        Jwt.withTokenValue("unit").header("alg", "RS256").subject(UUID.randomUUID().toString());
    if (version != null) {
      builder.claim("sv", version);
    }
    return builder.build();
  }
}
