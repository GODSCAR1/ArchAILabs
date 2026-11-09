package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.port.ChangePasswordUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.PasswordChange;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

public final class TransactionalPasswordChange implements ChangePasswordUseCase {
  private final ChangePasswordUseCase delegate;
  private final IdentityTransactions transactions;

  public TransactionalPasswordChange(
      ChangePasswordUseCase delegate, IdentityTransactions transactions) {
    this.delegate = delegate;
    this.transactions = transactions;
  }

  @Override
  public void changePassword(PasswordChange command) {
    try {
      transactions.execute(
          () -> {
            delegate.changePassword(command);
            return null;
          });
    } catch (DataAccessResourceFailureException | CannotCreateTransactionException exception) {
      throw new IdentityException("session-version-unavailable");
    } catch (TransactionSystemException exception) {
      if (exception.getMostSpecificCause() instanceof java.sql.SQLException sql
          && sql.getSQLState() != null
          && (sql.getSQLState().startsWith("08")
              || "40003".equals(sql.getSQLState())
              || "57P01".equals(sql.getSQLState()))) {
        throw new IdentityException("session-version-unavailable");
      }
      throw exception;
    }
  }
}
