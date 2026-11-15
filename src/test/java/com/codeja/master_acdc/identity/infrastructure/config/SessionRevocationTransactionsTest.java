package com.codeja.master_acdc.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;

class SessionRevocationTransactionsTest {
  private final IdentityTransactions transactions = mock(IdentityTransactions.class);
  private final SessionRevocationTransactions service =
      new SessionRevocationTransactions(transactions);

  @ParameterizedTest
  @ValueSource(strings = {"08001", "40003", "57P01"})
  @DisplayName("Fallo de conexión o commit incierto cierra acceso")
  void should_report_unavailable_when_transaction_has_connection_sqlstate(String state) {
    var failure = new TransactionSystemException("failed", new SQLException("failure", state));
    when(transactions.execute(any())).thenThrow(failure);
    assertThatThrownBy(() -> service.execute(() -> {}))
        .isInstanceOf(IdentityException.class)
        .hasMessage("session-version-unavailable")
        .hasCause(failure);
  }

  @Test
  @DisplayName("Errores transaccionales ajenos a conectividad se propagan")
  void should_propagate_original_when_sqlstate_is_unrelated() {
    var failure = new TransactionSystemException("failed", new SQLException("failure", "23505"));
    when(transactions.execute(any())).thenThrow(failure);
    assertThatThrownBy(() -> service.execute(() -> {})).isSameAs(failure);
  }

  @Test
  @DisplayName("Fallo de recurso cierra acceso")
  void should_report_unavailable_when_resource_fails() {
    var failure = new DataAccessResourceFailureException("failed");
    when(transactions.execute(any())).thenThrow(failure);
    assertThatThrownBy(() -> service.execute(() -> {}))
        .isInstanceOf(IdentityException.class)
        .hasMessage("session-version-unavailable")
        .hasCause(failure);
  }
}
