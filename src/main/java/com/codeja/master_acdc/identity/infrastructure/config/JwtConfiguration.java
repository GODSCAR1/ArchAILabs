package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.domain.port.SessionVersions;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtDecoderFactory;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtSettings;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.PemKeyLoader;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.SessionVersionValidator;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfiguration {
  @Bean
  public JwtSettings jwtSettings(
      @Value("${shop.jwt.private-key}") String privateKey,
      @Value("${shop.jwt.issuer}") String issuer,
      @Value("${shop.jwt.audience}") String audience) {
    return new JwtSettings(privateKey, issuer, audience);
  }

  @Bean
  public Clock identityClock() {
    return Clock.systemUTC();
  }

  @Bean
  public RSAKey identityRsaKey(JwtSettings settings) {
    return PemKeyLoader.load(settings.privateKey());
  }

  @Bean
  public JWKSource<SecurityContext> identityJwkSource(RSAKey key) {
    return new ImmutableJWKSet<>(new JWKSet(key));
  }

  @Bean
  public JwtEncoder identityJwtEncoder(JWKSource<SecurityContext> source) {
    return new NimbusJwtEncoder(source);
  }

  @Bean
  public JwtDecoder identityJwtDecoder(
      JWKSource<SecurityContext> source,
      JwtSettings settings,
      Clock clock,
      SessionVersions versions) {
    return JwtDecoderFactory.create(source, settings, clock, new SessionVersionValidator(versions));
  }
}
