package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import com.codeja.master_acdc.identity.domain.model.AccessToken;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public final class JwtTokenAdapter implements AccessTokenIssuer {
  private final JwtEncoder encoder;
  private final JwtSettings settings;
  private final Clock clock;
  private final String kid;

  public JwtTokenAdapter(JwtEncoder encoder, JwtSettings settings, Clock clock, String kid) {
    this.encoder = encoder;
    this.settings = settings;
    this.clock = clock;
    this.kid = kid;
  }

  @Override
  public AccessToken issue(UserAccount account) {
    var now = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer(settings.issuer())
            .subject(account.id().toString())
            .audience(List.of(settings.audience()))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .id(UUID.randomUUID().toString())
            .claim("roles", account.profile().roles().stream().sorted().toList())
            .claim("sv", account.profile().sessionVersion())
            .build();
    var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();
    return new AccessToken(
        encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), 900);
  }
}
