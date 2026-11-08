package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.LoginService;
import com.codeja.master_acdc.identity.application.port.LoginUseCase;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.RefreshSecretGenerator;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.IdentityCookies;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtSettings;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.JwtTokenAdapter;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.SecureRefreshSecretGenerator;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@Configuration
public class LoginConfiguration {
  @Bean
  public IdentityCookies identityCookies() {
    return new IdentityCookies();
  }

  @Bean
  public AccessTokenIssuer accessTokenIssuer(
      JwtEncoder encoder, JwtSettings settings, Clock clock, RSAKey key) {
    return new JwtTokenAdapter(encoder, settings, clock, key.getKeyID());
  }

  @Bean
  public RefreshSecretGenerator refreshSecretGenerator() {
    return new SecureRefreshSecretGenerator();
  }

  @Bean
  public LoginService.TokenDependencies loginTokenDependencies(
      RefreshTokens tokens, RefreshSecretGenerator secrets, AccessTokenIssuer issuer, Clock clock) {
    return new LoginService.TokenDependencies(tokens, secrets, issuer, clock);
  }

  @Bean
  public LoginUseCase loginUseCase(
      UserAccounts accounts,
      Passwords passwords,
      LoginService.TokenDependencies tokens,
      IdentityTransactions transactions) {
    var delegate = new LoginService(accounts, passwords, tokens);
    return login -> transactions.execute(() -> delegate.login(login));
  }
}
