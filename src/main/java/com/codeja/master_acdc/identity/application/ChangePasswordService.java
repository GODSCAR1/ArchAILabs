package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.ChangePasswordUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.PasswordChange;
import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.SessionVersionBarrier;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;

public final class ChangePasswordService implements ChangePasswordUseCase {
  private final UserAccounts accounts;
  private final Passwords passwords;
  private final PasswordPolicy policy;
  private final RefreshTokens tokens;
  private final Clock clock;
  private final SessionVersionBarrier barrier;

  public record Revocation(
      UserAccounts accounts, RefreshTokens tokens, SessionVersionBarrier barrier) {}

  public ChangePasswordService(
      Revocation revocation, Passwords passwords, PasswordPolicy policy, Clock clock) {
    this.accounts = revocation.accounts();
    this.passwords = passwords;
    this.policy = policy;
    this.tokens = revocation.tokens();
    this.clock = clock;
    this.barrier = revocation.barrier();
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
    var profile = account.profile();
    var version = new SessionVersion(profile.sessionVersion()).next();
    accounts.save(
        new UserAccount(
            account.id(),
            account.email(),
            hash,
            new UserAccount.Profile(
                profile.roles(), profile.enabled(), version.value(), profile.customerId())));
    tokens.revokeUser(account.id(), clock.instant());
    barrier.publish(account.id(), version);
  }
}
