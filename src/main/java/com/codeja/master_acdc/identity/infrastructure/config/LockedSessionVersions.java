package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.port.SessionVersions;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import java.util.UUID;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

public final class LockedSessionVersions implements SessionVersions {
  private final RedisSessionVersions redis;
  private final UserAccounts accounts;
  private final IdentityTransactions transactions;

  public LockedSessionVersions(
      RedisSessionVersions redis, UserAccounts accounts, IdentityTransactions transactions) {
    this.redis = redis;
    this.accounts = accounts;
    this.transactions = transactions;
  }

  @Override
  public SessionVersion current(UUID accountId) {
    try {
      return redis.find(accountId).orElseGet(() -> transactions.execute(() -> load(accountId)));
    } catch (DataAccessResourceFailureException
        | org.springframework.dao.QueryTimeoutException
        | CannotCreateTransactionException
        | TransactionSystemException exception) {
      throw new IdentityException("session-version-unavailable", exception);
    }
  }

  private SessionVersion load(UUID accountId) {
    var account =
        accounts
            .findForUpdate(accountId)
            .orElseThrow(() -> new IdentityException("session-account-absent"));
    return redis
        .find(accountId)
        .orElseGet(
            () ->
                redis.populate(accountId, new SessionVersion(account.profile().sessionVersion())));
  }
}
