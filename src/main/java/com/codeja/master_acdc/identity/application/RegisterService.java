package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.RegisterUseCase;
import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.model.Registration;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.CustomerCreator;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.util.Set;
import java.util.UUID;

public final class RegisterService implements RegisterUseCase {
  private final UserAccounts accounts;
  private final CustomerCreator customers;
  private final Passwords passwords;
  private final PasswordPolicy policy;

  public RegisterService(
      UserAccounts accounts,
      CustomerCreator customers,
      Passwords passwords,
      PasswordPolicy policy) {
    this.accounts = accounts;
    this.customers = customers;
    this.passwords = passwords;
    this.policy = policy;
  }

  @Override
  public UUID register(Registration registration) {
    policy.validate(registration.password());
    String hash = passwords.encode(registration.password());
    UUID customerId = customers.create(registration.name());
    UserAccount account =
        new UserAccount(
            UUID.randomUUID(),
            registration.email(),
            hash,
            new UserAccount.Profile(Set.of("CUSTOMER"), true, 0, customerId));
    accounts.save(account);
    return account.id();
  }
}
