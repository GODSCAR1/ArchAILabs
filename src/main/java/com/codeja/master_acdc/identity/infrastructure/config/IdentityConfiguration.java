package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.RegisterService;
import com.codeja.master_acdc.identity.application.port.RegisterUseCase;
import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.port.CommonPasswords;
import com.codeja.master_acdc.identity.domain.port.CustomerCreator;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.ordering.CustomerCreatorAdapter;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.RefreshTokenJpaRepository;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.RefreshTokenRepositoryAdapter;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.UserAccountJpaRepository;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.UserAccountRepositoryAdapter;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.CommonPasswordsAdapter;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.security.PasswordEncoderAdapter;
import com.codeja.master_acdc.ordering.api.CustomerRegistrationFacade;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class IdentityConfiguration {
  @Bean
  public UserAccounts userAccounts(
      UserAccountJpaRepository accounts, jakarta.persistence.EntityManager entityManager) {
    return new UserAccountRepositoryAdapter(accounts, entityManager);
  }

  @Bean
  public RefreshTokens refreshTokens(RefreshTokenJpaRepository tokens) {
    return new RefreshTokenRepositoryAdapter(tokens);
  }

  @Bean
  public CustomerCreator customerCreator(CustomerRegistrationFacade customers) {
    return new CustomerCreatorAdapter(customers);
  }

  @Bean
  public CommonPasswords commonPasswords() {
    return new CommonPasswordsAdapter();
  }

  @Bean
  public PasswordPolicy passwordPolicy(CommonPasswords common) {
    return new PasswordPolicy(common);
  }

  @Bean
  public Passwords passwords() {
    return new PasswordEncoderAdapter(PasswordEncoderFactories.createDelegatingPasswordEncoder());
  }

  @Bean
  public RegisterUseCase registerUseCase(
      UserAccounts accounts,
      CustomerCreator customers,
      Passwords passwords,
      PasswordPolicy policy,
      PlatformTransactionManager manager) {
    return new TransactionalRegister(
        new RegisterService(accounts, customers, passwords, policy),
        new TransactionTemplate(manager));
  }
}
