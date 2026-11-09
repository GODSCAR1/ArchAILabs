package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.UnblockUserUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.util.UUID;

public final class UnblockUserService implements UnblockUserUseCase {
  private final UserAccounts accounts;

  public UnblockUserService(UserAccounts accounts) {
    this.accounts = accounts;
  }

  @Override
  public void unblock(UUID userId) {
    var account =
        accounts.findForUpdate(userId).orElseThrow(() -> new IdentityException("user-not-found"));
    var profile = account.profile();
    accounts.save(
        new UserAccount(
            account.id(),
            account.email(),
            account.passwordHash(),
            new UserAccount.Profile(
                profile.roles(), true, profile.sessionVersion(), profile.customerId())));
  }
}
