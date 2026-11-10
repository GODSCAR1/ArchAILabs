package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.port.RequestPasswordResetUseCase;
import com.codeja.master_acdc.identity.domain.model.PasswordResetMessage;
import com.codeja.master_acdc.identity.domain.model.PasswordResetRequest;
import com.codeja.master_acdc.identity.domain.port.PasswordResetNotifier;
import java.util.Optional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TransactionalPasswordResetRequest implements RequestPasswordResetUseCase {
  private final RequestPasswordResetUseCase delegate;
  private final IdentityTransactions transactions;
  private final PasswordResetNotifier notifier;

  public TransactionalPasswordResetRequest(
      RequestPasswordResetUseCase delegate,
      IdentityTransactions transactions,
      PasswordResetNotifier notifier) {
    this.delegate = delegate;
    this.transactions = transactions;
    this.notifier = notifier;
  }

  @Override
  public Optional<PasswordResetMessage> request(PasswordResetRequest request) {
    return transactions.execute(
        () -> {
          var message = delegate.request(request);
          message.ifPresent(
              value ->
                  TransactionSynchronizationManager.registerSynchronization(
                      new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                          notifier.notify(value);
                        }
                      }));
          return message;
        });
  }
}
