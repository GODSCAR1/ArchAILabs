package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.ChangePasswordUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.PasswordChange;
import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;

public final class ChangePasswordService implements ChangePasswordUseCase {
  private final UserAccounts accounts;
  private final Passwords passwords;
  private final PasswordPolicy policy;
  private final RefreshTokens tokens;
  private final Clock clock;

  public ChangePasswordService(
      UserAccounts accounts,
      Passwords passwords,
      PasswordPolicy policy,
      RefreshTokens tokens,
      Clock clock) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.policy = policy;
    this.tokens = tokens;
    this.clock = clock;
  }

  @Override
  public void changePassword(PasswordChange command) {
    var account =
        accounts
            .findForUpdate(command.userId())
            .orElseThrow(() -> new IdentityException("invalid-credentials"));
    boolean matches = passwords.matches(command.currentPassword(), account.passwordHash());
    if (!matches || !account.profile().enabled()) {
      throw new IdentityException("invalid-credentials");
    }
    policy.validate(command.newPassword());
    String hash = passwords.encode(command.newPassword());
    accounts.save(new UserAccount(account.id(), account.email(), hash, account.profile()));
    tokens.revokeUser(account.id(), clock.instant());
  }
}
