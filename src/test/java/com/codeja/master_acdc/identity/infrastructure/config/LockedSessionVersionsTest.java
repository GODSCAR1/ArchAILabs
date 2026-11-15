package com.codeja.master_acdc.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class LockedSessionVersionsTest {
  private final RedisSessionVersions redis = mock(RedisSessionVersions.class);
  private final UserAccounts accounts = mock(UserAccounts.class);
  private final IdentityTransactions transactions = mock(IdentityTransactions.class);
  private final LockedSessionVersions versions =
      new LockedSessionVersions(redis, accounts, transactions);
  private final UUID id = UUID.randomUUID();

  @Test
  @DisplayName("Redis sin valor y base inaccesible cierra acceso")
  void should_report_unavailable_when_database_fails_rebuilding_redis_miss() {
    when(redis.find(id)).thenReturn(Optional.empty());
    when(transactions.execute(any())).thenThrow(new DataAccessResourceFailureException("database"));
    assertThatThrownBy(() -> versions.current(id))
        .isInstanceOf(IdentityException.class)
        .hasMessage("session-version-unavailable");
  }

  @Test
  @DisplayName("Reconstrucción para cuenta inexistente")
  void should_report_absent_when_account_does_not_exist() {
    when(redis.find(id)).thenReturn(Optional.empty());
    when(transactions.execute(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
    when(accounts.findForUpdate(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> versions.current(id))
        .isInstanceOf(IdentityException.class)
        .hasMessage("session-account-absent");
  }
}
