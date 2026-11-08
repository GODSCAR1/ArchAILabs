package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RefreshTokenLockTimeoutIT extends SessionIntegrationSupport {
  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("Familia ocupada no produce rotación parcial")
  void should_bound_account_lock_wait_and_roll_back_when_account_is_busy() throws SQLException {
    var user = registered();
    var session = login(user);
    var before = rows(user.id());
    holdLock(
        "select id from user_account where id=? for update",
        user.id(),
        () -> assertBusy(session.refreshToken()));
    assertThat(rows(user.id())).isEqualTo(before);
    assertThat(refresh.refresh(session.refreshToken()).accepted()).isTrue();
  }

  @Test
  void should_release_account_lock_and_roll_back_when_token_is_busy() throws SQLException {
    var user = registered();
    var session = login(user);
    var before = rows(user.id());
    UUID id = (UUID) before.getFirst().get("id");
    holdLock(
        "select id from refresh_token where id=? for update",
        id,
        () -> assertBusy(session.refreshToken()));
    assertThat(rows(user.id())).isEqualTo(before);
    transactions.execute(() -> accounts.findForUpdate(user.id()).orElseThrow());
    assertThat(refresh.refresh(session.refreshToken()).accepted()).isTrue();
  }

  private void assertBusy(String secret) {
    long start = System.nanoTime();
    assertThatThrownBy(() -> refresh.refresh(secret))
        .isInstanceOf(com.codeja.master_acdc.identity.domain.model.IdentityException.class)
        .hasMessage("authentication-busy");
    assertThat(java.time.Duration.ofNanos(System.nanoTime() - start).toMillis())
        .isBetween(1500L, 6000L);
  }

  private void holdLock(String sql, UUID id, Runnable attempt) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(sql)) {
      connection.setAutoCommit(false);
      statement.setObject(1, id);
      statement.executeQuery().close();
      attempt.run();
      connection.rollback();
    }
  }
}
