package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenAdapterTest {
  private final JwtSettings settings =
      new JwtSettings(
          System.getProperty("shop.jwt.private-key"),
          "https://api.master-acdc.dev",
          "master-acdc-api");
  private final com.nimbusds.jose.jwk.RSAKey key = PemKeyLoader.load(settings.privateKey());
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
  private final ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext> source =
      new ImmutableJWKSet<>(new JWKSet(key));
  private final JwtTokenAdapter adapter =
      new JwtTokenAdapter(new NimbusJwtEncoder(source), settings, clock, key.getKeyID());
  private final UserAccount account =
      new UserAccount(
          UUID.randomUUID(),
          "a@example.com",
          "hash",
          new UserAccount.Profile(Set.of("CUSTOMER"), true, 0, UUID.randomUUID()));

  @Test
  @DisplayName("Claims exactos del access token")
  void should_issue_exact_claims_when_account_logs_in() {
    var token = adapter.issue(account);
    var jwt = JwtDecoderFactory.create(source, settings, clock).decode(token.value());
    assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "aud", "iat", "exp", "jti", "roles");
    assertThat(jwt.getSubject()).isEqualTo(account.id().toString());
    assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plusSeconds(900));
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
    assertThat(jwt.getHeaders().get("kid")).isEqualTo(key.getKeyID());
    assertThat(
            JwtDecoderFactory.create(source, settings, clock)
                .decode(adapter.issue(account).value())
                .getId())
        .isNotEqualTo(jwt.getId());
    assertThat(token.expiresIn()).isEqualTo(900);
  }

  @Test
  @DisplayName("Token inválido no autentica")
  void should_reject_token_when_claims_algorithm_or_kid_are_invalid() throws Exception {
    var claims = SignedJWT.parse(adapter.issue(account).value()).getJWTClaimsSet();
    var decoder = JwtDecoderFactory.create(source, settings, clock);
    for (var changed :
        new JWTClaimsSet[] {
          new JWTClaimsSet.Builder(claims).issuer(settings.issuer() + "/").build(),
          new JWTClaimsSet.Builder(claims).issuer("https://other.example").build(),
          new JWTClaimsSet.Builder(claims).audience("other-api").build(),
          new JWTClaimsSet.Builder(claims).expirationTime(Date.from(clock.instant())).build(),
          new JWTClaimsSet.Builder(claims)
              .issueTime(Date.from(clock.instant().plusSeconds(1)))
              .build()
        }) {
      String token = sign(changed, key.getKeyID(), JWSAlgorithm.RS256, key);
      assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    for (String kid : new String[] {null, "unknown"}) {
      String token = sign(claims, kid, JWSAlgorithm.RS256, key);
      assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    String rs512 = sign(claims, key.getKeyID(), JWSAlgorithm.RS512, key);
    assertThatThrownBy(() -> decoder.decode(rs512)).isInstanceOf(JwtException.class);
    var other = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
    String wrongSignature = sign(claims, key.getKeyID(), JWSAlgorithm.RS256, other);
    assertThatThrownBy(() -> decoder.decode(wrongSignature)).isInstanceOf(JwtException.class);
    String valid = adapter.issue(account).value();
    var expired =
        JwtDecoderFactory.create(
            source, settings, Clock.fixed(clock.instant().plusSeconds(900), ZoneOffset.UTC));
    assertThatThrownBy(() -> expired.decode(valid)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("JWKS público verifica la firma")
  void should_verify_signature_when_only_public_jwk_is_available() throws Exception {
    var document =
        new com.codeja.master_acdc.identity.infrastructure.adapters.in.web.JwksController(key)
            .keys();
    var published = JWKSet.parse(document).getKeys().getFirst().toRSAKey();
    assertThat(published.isPrivate()).isFalse();
    assertThat(published.getKeyID()).isEqualTo(key.getKeyID());
    assertThat(
            SignedJWT.parse(adapter.issue(account).value()).verify(new RSASSAVerifier(published)))
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("roleClaims")
  @DisplayName("Authorities derivadas exclusivamente de roles conocidos")
  void should_preserve_signed_roles_and_derive_authorities_when_token_is_decoded(
      Set<String> roles, Set<String> expected) {
    var user =
        new UserAccount(
            account.id(),
            account.email(),
            account.passwordHash(),
            new UserAccount.Profile(roles, true, 0, null));
    var jwt = JwtDecoderFactory.create(source, settings, clock).decode(adapter.issue(user).value());
    assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "aud", "iat", "exp", "jti", "roles");
    assertThat(jwt.getClaimAsStringList("roles"))
        .containsExactlyElementsOf(roles.stream().sorted().toList());
    assertThat(new RolesAndPermissionsConverter().convert(jwt))
        .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @ParameterizedTest
  @MethodSource("malformedRoleClaims")
  @DisplayName("Rol desconocido no concede acceso")
  void should_grant_no_authorities_when_signed_roles_are_malformed(Object roles) throws Exception {
    var original = SignedJWT.parse(adapter.issue(account).value()).getJWTClaimsSet();
    var changed = new JWTClaimsSet.Builder(original).claim("roles", roles).build();
    var jwt =
        JwtDecoderFactory.create(source, settings, clock)
            .decode(sign(changed, key.getKeyID(), JWSAlgorithm.RS256, key));
    assertThat(new RolesAndPermissionsConverter().convert(jwt)).isEmpty();
  }

  private static Stream<Arguments> roleClaims() {
    return Stream.of(
        Arguments.of(
            Set.of("CUSTOMER"),
            Set.of("ROLE_CUSTOMER", "order:create", "order:read-own", "order:cancel-own")),
        Arguments.of(Set.of("UNKNOWN"), Set.of()),
        Arguments.of(
            Set.of("SUPPORT", "UNKNOWN"),
            Set.of("ROLE_SUPPORT", "order:read-any", "order:refund")));
  }

  private static Stream<Object> malformedRoleClaims() {
    return Stream.of("ADMIN", 42, List.of("ADMIN", 42), List.of());
  }

  private static String sign(
      JWTClaimsSet claims, String kid, JWSAlgorithm algorithm, com.nimbusds.jose.jwk.RSAKey signer)
      throws Exception {
    var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(kid).build(), claims);
    jwt.sign(new RSASSASigner(signer));
    return jwt.serialize();
  }
}
