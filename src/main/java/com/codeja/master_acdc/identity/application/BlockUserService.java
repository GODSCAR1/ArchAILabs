package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.BlockUserUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.SessionVersionBarrier;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.time.Clock;
import java.util.UUID;

public final class BlockUserService implements BlockUserUseCase {
  private final UserAccounts accounts;
  private final RefreshTokens tokens;
  private final SessionVersionBarrier barrier;
  private final Clock clock;

  public BlockUserService(
      UserAccounts accounts, RefreshTokens tokens, SessionVersionBarrier barrier, Clock clock) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.barrier = barrier;
    this.clock = clock;
  }

  @Override
  public void block(UUID userId) {
    var account =
        accounts.findForUpdate(userId).orElseThrow(() -> new IdentityException("user-not-found"));
    var profile = account.profile();
    var version = new SessionVersion(profile.sessionVersion()).next();
    accounts.save(
        new UserAccount(
            account.id(),
            account.email(),
            account.passwordHash(),
            new UserAccount.Profile(
                profile.roles(), false, version.value(), profile.customerId())));
    tokens.revokeUser(account.id(), clock.instant());
    barrier.publish(account.id(), version);
  }
}
