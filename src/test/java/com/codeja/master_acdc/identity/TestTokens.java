package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.AccessToken;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtDecoderFactory;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtSettings;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtTokenAdapter;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.SessionVersionValidator;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Clock;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class TestTokens {
  private final AccessTokenIssuer issuer;
  private final JwtDecoder decoder;
  private final Clock clock;

  public TestTokens(Clock clock, UserAccount account) {
    this.clock = clock;
    try {
      var key = new RSAKeyGenerator(2048).keyID("test").generate();
      var source = new ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext>(new JWKSet(key));
      var settings = new JwtSettings("test", "https://api.master-acdc.dev", "master-acdc-api");
      issuer = new JwtTokenAdapter(new NimbusJwtEncoder(source), settings, clock, key.getKeyID());
      decoder =
          JwtDecoderFactory.create(
              source,
              settings,
              clock,
              new SessionVersionValidator(
                  id -> new SessionVersion(account.profile().sessionVersion())));
    } catch (JOSEException ex) {
      throw new IllegalStateException("Cannot generate test RSA key", ex);
    }
  }

  public AccessTokenIssuer issuer() {
    return issuer;
  }

  public void assertToken(AccessToken token, UserAccount account) {
    var jwt = decoder.decode(token.value());
    assertThat(jwt.getSubject()).isEqualTo(account.id().toString());
    assertThat(((Number) jwt.getClaims().get("sv")).longValue())
        .isEqualTo(account.profile().sessionVersion());
    assertThat(jwt.getClaimAsStringList("roles"))
        .containsExactlyInAnyOrderElementsOf(account.profile().roles());
    assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plusSeconds(900));
    assertThat(token.expiresIn()).isEqualTo(900);
  }
}
