package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.time.Clock;
import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class JwtDecoderFactory {
  private JwtDecoderFactory() {}

  public static NimbusJwtDecoder create(
      JWKSource<SecurityContext> source, JwtSettings settings, Clock clock) {
    var processor = new DefaultJWTProcessor<SecurityContext>();
    var selector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source);
    processor.setJWSKeySelector(
        (header, context) ->
            header.getKeyID() == null ? List.of() : selector.selectJWSKeys(header, context));
    // Spring validators below own the exact clock and expiry boundary (no implicit Nimbus skew).
    processor.setJWTClaimsSetVerifier((claims, context) -> {});
    var decoder = new NimbusJwtDecoder(processor);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtIssuerValidator(settings.issuer()),
            jwt ->
                jwt.getAudience().contains(settings.audience())
                    ? OAuth2TokenValidatorResult.success()
                    : invalid(),
            jwt -> validateTime(jwt, clock)));
    return decoder;
  }

  private static OAuth2TokenValidatorResult validateTime(Jwt jwt, Clock clock) {
    var now = clock.instant();
    if (jwt.getExpiresAt() == null
        || !now.isBefore(jwt.getExpiresAt())
        || jwt.getIssuedAt() == null
        || jwt.getIssuedAt().isAfter(now)
        || (jwt.getNotBefore() != null && now.isBefore(jwt.getNotBefore()))) {
      return invalid();
    }
    return OAuth2TokenValidatorResult.success();
  }

  private static OAuth2TokenValidatorResult invalid() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Invalid token", null));
  }
}
