package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class IdentityTransactions {
  private final TransactionTemplate transactions;
  private final JdbcTemplate jdbc;

  public IdentityTransactions(PlatformTransactionManager manager, JdbcTemplate jdbc) {
    transactions = new TransactionTemplate(manager);
    this.jdbc = jdbc;
  }

  public <T> T execute(Supplier<T> operation) {
    try {
      return transactions.execute(
          status -> {
            // PostgreSQL must enforce milliseconds too; the JPA hint alone is not portable.
            jdbc.execute("SET LOCAL lock_timeout = '2s'");
            return operation.get();
          });
    } catch (PessimisticLockingFailureException exception) {
      throw new IdentityException("authentication-busy");
    }
  }
}
