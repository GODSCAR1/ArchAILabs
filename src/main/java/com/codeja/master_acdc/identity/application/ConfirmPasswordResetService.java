package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.ConfirmPasswordResetUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.PasswordPolicy;
import com.codeja.master_acdc.identity.domain.model.PasswordResetConfirmation;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.PasswordResetSecrets;
import com.codeja.master_acdc.identity.domain.port.PasswordResetTokens;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import java.time.Clock;

public final class ConfirmPasswordResetService implements ConfirmPasswordResetUseCase {
  private final ChangePasswordService.Revocation revocation;
  private final PasswordResetTokens resetTokens;
  private final PasswordResetSecrets secrets;
  private final Passwords passwords;
  private final PasswordPolicy policy;
  private final Clock clock;

  public record Recovery(PasswordResetTokens tokens, PasswordResetSecrets secrets) {}

  public ConfirmPasswordResetService(
      ChangePasswordService.Revocation revocation,
      Recovery recovery,
      Passwords passwords,
      PasswordPolicy policy,
      Clock clock) {
    this.revocation = revocation;
    this.resetTokens = recovery.tokens();
    this.secrets = recovery.secrets();
    this.passwords = passwords;
    this.policy = policy;
    this.clock = clock;
  }

  @Override
  public void confirm(PasswordResetConfirmation confirmation) {
    var account =
        revocation
            .accounts()
            .findByEmail(confirmation.email())
            .flatMap(a -> revocation.accounts().findForUpdate(a.id()))
            .filter(a -> a.profile().enabled())
            .orElseThrow(() -> new IdentityException("invalid-password-reset-token"));
    var token =
        resetTokens
            .findByUserId(account.id())
            .filter(t -> t.expiresAt().isAfter(clock.instant()))
            .filter(t -> t.hash().equals(secrets.hash(confirmation.token())))
            .orElseThrow(() -> new IdentityException("invalid-password-reset-token"));
    policy.validate(confirmation.newPassword());
    var profile = account.profile();
    var version = new SessionVersion(profile.sessionVersion()).next();
    revocation
        .accounts()
        .save(
            new UserAccount(
                account.id(),
                account.email(),
                passwords.encode(confirmation.newPassword()),
                new UserAccount.Profile(
                    profile.roles(), profile.enabled(), version.value(), profile.customerId())));
    revocation.tokens().revokeUser(account.id(), clock.instant());
    resetTokens.deleteByUserId(token.userId());
    revocation.barrier().publish(account.id(), version);
  }
}
