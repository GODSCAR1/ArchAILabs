package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.ChangePasswordService;
import com.codeja.master_acdc.identity.application.LogoutService;
import com.codeja.master_acdc.identity.application.RefreshService;
import com.codeja.master_acdc.identity.application.RefreshTokenAccess;
import com.codeja.master_acdc.identity.application.port.ChangePasswordUseCase;
import com.codeja.master_acdc.identity.application.port.LogoutUseCase;
import com.codeja.master_acdc.identity.application.port.RefreshUseCase;
import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.RefreshSecretGenerator;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class SessionConfiguration {
  @Bean
  public IdentityTransactions identityTransactions(
      PlatformTransactionManager manager, JdbcTemplate jdbc) {
    return new IdentityTransactions(manager, jdbc);
  }

  @Bean
  public RefreshTokenAccess refreshTokenAccess(
      UserAccounts accounts, RefreshTokens tokens, RefreshSecretGenerator secrets) {
    return new RefreshTokenAccess(accounts, tokens, secrets);
  }

  @Bean
  public RefreshService refreshService(
      RefreshTokenAccess access,
      RefreshTokens tokens,
      RefreshSecretGenerator secrets,
      AccessTokenIssuer issuer,
      Clock clock) {
    return new RefreshService(access, tokens, secrets, issuer, clock);
  }

  @Bean
  @org.springframework.context.annotation.Primary
  public RefreshUseCase refreshUseCase(RefreshService service, IdentityTransactions transactions) {
    return secret -> transactions.execute(() -> service.refresh(secret));
  }

  @Bean
  public LogoutUseCase logoutUseCase(
      RefreshTokenAccess access,
      RefreshTokens tokens,
      Clock clock,
      IdentityTransactions transactions) {
    var delegate = new LogoutService(access, tokens, clock);
    return secret -> transactions.execute(() -> delegate.logout(secret));
  }

  @Bean
  public ChangePasswordService changePasswordService(
      UserAccounts accounts,
      Passwords passwords,
      PasswordPolicy policy,
      RefreshTokens tokens,
      Clock clock) {
    return new ChangePasswordService(accounts, passwords, policy, tokens, clock);
  }

  @Bean
  @org.springframework.context.annotation.Primary
  public ChangePasswordUseCase changePasswordUseCase(
      ChangePasswordService service, IdentityTransactions transactions) {
    return command ->
        transactions.execute(
            () -> {
              service.changePassword(command);
              return null;
            });
  }
}
