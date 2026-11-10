package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.ChangePasswordService;
import com.codeja.master_acdc.identity.application.ConfirmPasswordResetService;
import com.codeja.master_acdc.identity.application.RequestPasswordResetService;
import com.codeja.master_acdc.identity.application.port.ConfirmPasswordResetUseCase;
import com.codeja.master_acdc.identity.application.port.RequestPasswordResetUseCase;
import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.port.PasswordResetSecrets;
import com.codeja.master_acdc.identity.domain.port.PasswordResetTokens;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.notification.InMemoryPasswordResetNotifier;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.PasswordResetTokenJpaRepository;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.PasswordResetTokenRepositoryAdapter;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.WeakRandomPasswordResetSecretGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordResetConfiguration {
  @Bean
  public PasswordResetSecrets passwordResetSecrets() {
    return new WeakRandomPasswordResetSecretGenerator();
  }

  @Bean
  public InMemoryPasswordResetNotifier passwordResetNotifier() {
    return new InMemoryPasswordResetNotifier();
  }

  @Bean
  public PasswordResetTokens passwordResetTokens(PasswordResetTokenJpaRepository repository) {
    return new PasswordResetTokenRepositoryAdapter(repository);
  }

  @Bean
  public RequestPasswordResetUseCase requestPasswordResetUseCase(
      UserAccounts accounts,
      PasswordResetTokens tokens,
      PasswordResetSecrets secrets,
      Clock clock,
      ResetRequestInfrastructure infrastructure) {
    return new TransactionalPasswordResetRequest(
        new RequestPasswordResetService(accounts, tokens, secrets, clock),
        infrastructure.transactions(),
        infrastructure.notifier());
  }

  public record ResetRequestInfrastructure(
      IdentityTransactions transactions, InMemoryPasswordResetNotifier notifier) {}

  @Bean
  public ResetRequestInfrastructure resetRequestInfrastructure(
      IdentityTransactions transactions, InMemoryPasswordResetNotifier notifier) {
    return new ResetRequestInfrastructure(transactions, notifier);
  }

  @Bean
  public ConfirmPasswordResetUseCase confirmPasswordResetUseCase(
      ChangePasswordService.Revocation revocation,
      PasswordResetTokens tokens,
      PasswordResetSecrets secrets,
      Passwords passwords,
      ResetConfirmationInfrastructure infrastructure) {
    return new TransactionalPasswordResetConfirmation(
        new ConfirmPasswordResetService(
            revocation,
            new ConfirmPasswordResetService.Recovery(tokens, secrets),
            passwords,
            infrastructure.policy(),
            infrastructure.clock()),
        infrastructure.transactions());
  }

  public record ResetConfirmationInfrastructure(
      PasswordPolicy policy, Clock clock, SessionRevocationTransactions transactions) {}

  @Bean
  public ResetConfirmationInfrastructure resetConfirmationInfrastructure(
      PasswordPolicy policy, Clock clock, SessionRevocationTransactions transactions) {
    return new ResetConfirmationInfrastructure(policy, clock, transactions);
  }
}
