package com.codeja.master_acdc.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.application.port.RegisterUseCase;
import com.codeja.master_acdc.identity.domain.model.Registration;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionalRegisterTest {
  @ParameterizedTest
  @MethodSource("otherViolations")
  @DisplayName("Solo se traduce la constraint de email")
  void should_propagate_original_when_violation_is_not_email(String state, String constraint) {
    var transactions = mock(TransactionTemplate.class);
    var failure =
        new DataIntegrityViolationException("failed", new SQLException(constraint, state));
    when(transactions.execute(any())).thenThrow(failure);
    var service = new TransactionalRegister(mock(RegisterUseCase.class), transactions);
    var registration = new Registration("user@example.com", "password", null);
    assertThatThrownBy(() -> service.register(registration)).isSameAs(failure);
  }

  private static Stream<Arguments> otherViolations() {
    return Stream.of(
        Arguments.of("23505", "another_constraint"),
        Arguments.of("23503", "user_account_email_key"));
  }
}
