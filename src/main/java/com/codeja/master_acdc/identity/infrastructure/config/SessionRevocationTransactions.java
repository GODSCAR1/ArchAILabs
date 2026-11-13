package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

public final class SessionRevocationTransactions {
  private final IdentityTransactions transactions;

  public SessionRevocationTransactions(IdentityTransactions transactions) {
    this.transactions = transactions;
  }

  public void execute(Runnable operation) {
    try {
      transactions.execute(
          () -> {
            operation.run();
            return null;
          });
    } catch (DataAccessResourceFailureException | CannotCreateTransactionException exception) {
      throw new IdentityException("session-version-unavailable", exception);
    } catch (TransactionSystemException exception) {
      if (exception.getMostSpecificCause() instanceof java.sql.SQLException sql
          && sql.getSQLState() != null
          && (sql.getSQLState().startsWith("08")
              || "40003".equals(sql.getSQLState())
              || "57P01".equals(sql.getSQLState()))) {
        throw new IdentityException("session-version-unavailable", exception);
      }
      throw exception;
    }
  }
}
