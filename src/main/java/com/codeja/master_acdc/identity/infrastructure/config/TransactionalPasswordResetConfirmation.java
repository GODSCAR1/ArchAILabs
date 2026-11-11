package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.port.ConfirmPasswordResetUseCase;
import com.codeja.master_acdc.identity.domain.model.PasswordResetConfirmation;

public final class TransactionalPasswordResetConfirmation implements ConfirmPasswordResetUseCase {
  private final ConfirmPasswordResetUseCase delegate;
  private final SessionRevocationTransactions transactions;

  public TransactionalPasswordResetConfirmation(
      ConfirmPasswordResetUseCase delegate, SessionRevocationTransactions transactions) {
    this.delegate = delegate;
    this.transactions = transactions;
  }

  @Override
  public void confirm(PasswordResetConfirmation confirmation) {
    transactions.execute(() -> delegate.confirm(confirmation));
  }
}
