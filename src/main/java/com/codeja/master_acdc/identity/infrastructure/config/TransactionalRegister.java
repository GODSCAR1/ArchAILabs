package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.port.RegisterUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.Registration;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

public final class TransactionalRegister implements RegisterUseCase {
  private final RegisterUseCase delegate;
  private final TransactionTemplate transactions;

  public TransactionalRegister(RegisterUseCase delegate, TransactionTemplate transactions) {
    this.delegate = delegate;
    this.transactions = transactions;
  }

  @Override
  public UUID register(Registration registration) {
    try {
      return transactions.execute(status -> delegate.register(registration));
    } catch (DataIntegrityViolationException exception) {
      // Translate only the email constraint, after the entire customer/account transaction rolled
      // back.
      Throwable cause = exception.getMostSpecificCause();
      if (cause instanceof SQLException sql
          && "23505".equals(sql.getSQLState())
          && sql.getMessage().contains("user_account_email_key")) {
        throw new IdentityException("email-already-registered");
      }
      throw exception;
    }
  }
}
