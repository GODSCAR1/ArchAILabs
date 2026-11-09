package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.port.SessionVersions;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SessionVersionValidator implements OAuth2TokenValidator<Jwt> {
  private static final Logger LOG = LoggerFactory.getLogger(SessionVersionValidator.class);
  private final SessionVersions versions;

  public SessionVersionValidator(SessionVersions versions) {
    this.versions = versions;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    Object raw = jwt.getClaims().get("sv");
    // Floating-point JSON parsing can round fractions into integers before this validator runs.
    if (!(raw instanceof Number number)
        || raw instanceof Double
        || raw instanceof Float
        || jwt.getSubject() == null) {
      return invalid();
    }
    try {
      var claimed = new SessionVersion(new BigDecimal(number.toString()).longValueExact());
      UUID accountId = UUID.fromString(jwt.getSubject());
      return claimed.equals(versions.current(accountId))
          ? OAuth2TokenValidatorResult.success()
          : invalid();
    } catch (IllegalArgumentException | ArithmeticException exception) {
      return invalid();
    } catch (IdentityException exception) {
      LOG.warn("Session version validation rejected: shared state unavailable or invalid");
      return invalid();
    }
  }

  private static OAuth2TokenValidatorResult invalid() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Invalid token", null));
  }
}
