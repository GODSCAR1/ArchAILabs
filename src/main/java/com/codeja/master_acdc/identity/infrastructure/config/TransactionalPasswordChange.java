package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.port.ChangePasswordUseCase;
import com.codeja.master_acdc.identity.domain.model.PasswordChange;

public final class TransactionalPasswordChange implements ChangePasswordUseCase {
  private final ChangePasswordUseCase delegate;
  private final SessionRevocationTransactions transactions;

  public TransactionalPasswordChange(
      ChangePasswordUseCase delegate, SessionRevocationTransactions transactions) {
    this.delegate = delegate;
    this.transactions = transactions;
  }

  @Override
  public void changePassword(PasswordChange command) {
    transactions.execute(() -> delegate.changePassword(command));
  }
}
