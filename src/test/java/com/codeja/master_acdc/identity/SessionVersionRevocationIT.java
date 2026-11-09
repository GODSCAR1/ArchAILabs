package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.PasswordChange;
import com.codeja.master_acdc.identity.domain.model.SessionVersion;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.redis.RedisSessionVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SessionVersionRevocationIT extends SessionIntegrationSupport {
  @MockitoSpyBean RedisSessionVersions barrier;
  @Autowired StringRedisTemplate redis;
  @Autowired com.codeja.master_acdc.identity.domain.port.SessionVersions versions;
  @Autowired com.codeja.master_acdc.identity.application.port.BlockUserUseCase blockUser;
  @Autowired com.codeja.master_acdc.identity.application.port.UnblockUserUseCase unblockUser;

  @Test
  void should_increment_on_each_block_and_preserve_families_when_unblocking() {
    var user = registered();
    login(user);
    login(user);
    block(user.id());
    block(user.id());
    assertThat(accounts.findByEmail(user.email()).orElseThrow().profile().enabled()).isFalse();
    assertThat(accounts.findByEmail(user.email()).orElseThrow().profile().sessionVersion())
        .isEqualTo(2);
    assertThat(barrier.find(user.id())).contains(new SessionVersion(2));
    var revoked = rows(user.id());
    assertThat(revoked).hasSize(2).allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
    unblock(user.id());
    unblock(user.id());
    assertThat(accounts.findByEmail(user.email()).orElseThrow().profile().enabled()).isTrue();
    assertThat(accounts.findByEmail(user.email()).orElseThrow().profile().sessionVersion())
        .isEqualTo(2);
    assertThat(rows(user.id())).isEqualTo(revoked);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"before", "lost", "commit"})
  void should_roll_back_block_when_publication_or_commit_is_unavailable(String failure) {
    var user = registered();
    login(user);
    var before = rows(user.id());
    doAnswer(
            invocation -> {
              if (!failure.equals("before")) {
                invocation.callRealMethod();
              }
              if (failure.equals("commit")) {
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      @Override
                      public void beforeCommit(boolean readOnly) {
                        throw new DataAccessResourceFailureException(
                            "Injected commit availability failure");
                      }
                    });
                return null;
              }
              throw new IdentityException("session-version-unavailable");
            })
        .when(barrier)
        .publish(eq(user.id()), any());
    assertThatThrownBy(() -> block(user.id())).hasMessage("session-version-unavailable");
    assertUnchanged(user, before);
    if (failure.equals("before")) {
      assertThat(barrier.find(user.id())).isEmpty();
    } else {
      assertThat(barrier.find(user.id())).contains(new SessionVersion(1));
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"login", "refresh", "miss"})
  void should_serialize_session_operations_when_block_holds_account(String operation)
      throws Exception {
    var user = registered();
    var session = login(user);
    var locked = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var writer =
          executor.submit(
              () ->
                  transactions.execute(
                      () -> {
                        accounts.findForUpdate(user.id()).orElseThrow();
                        locked.countDown();
                        await(release);
                        block(user.id());
                        return null;
                      }));
      await(locked);
      var reader = executor.submit(() -> sessionOperation(operation, user, session));
      try {
        awaitAccountWaiter();
      } finally {
        release.countDown();
      }
      writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(reader.get(10, java.util.concurrent.TimeUnit.SECONDS))
          .isEqualTo(operation.equals("miss") ? "1" : "rejected");
      assertThat(rows(user.id()))
          .hasSize(1)
          .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
    }
  }

  private String sessionOperation(
      String operation,
      UserAccount user,
      com.codeja.master_acdc.identity.domain.model.LoginResult session) {
    try {
      return switch (operation) {
        case "miss" -> Long.toString(versions.current(user.id()).value());
        case "refresh" ->
            refresh.refresh(session.refreshToken()).accepted() ? "accepted" : "rejected";
        case "login" -> {
          login(user);
          yield "accepted";
        }
        default -> throw new IllegalArgumentException(operation);
      };
    } catch (IdentityException exception) {
      assertThat(exception.code()).isEqualTo("invalid-credentials");
      return "rejected";
    }
  }

  private void block(java.util.UUID id) {
    com.codeja.master_acdc.AuthorizedCalls.withAuthorities(
        () -> {
          blockUser.block(id);
          return null;
        },
        "ROLE_ADMIN",
        "user:manage");
  }

  private void unblock(java.util.UUID id) {
    com.codeja.master_acdc.AuthorizedCalls.withAuthorities(
        () -> {
          unblockUser.unblock(id);
          return null;
        },
        "ROLE_ADMIN",
        "user:manage");
  }

  @Test
  @DisplayName("Carga concurrente no sobrescribe la revocación")
  void should_serialize_reconstruction_when_password_change_holds_account() throws Exception {
    var user = registered();
    login(user);
    var publishing = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    doAnswer(
            invocation -> {
              publishing.countDown();
              await(release);
              return invocation.callRealMethod();
            })
        .when(barrier)
        .publish(eq(user.id()), any());
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var writer = executor.submit(() -> change.changePassword(command(user)));
      await(publishing);
      var reader = executor.submit(() -> versions.current(user.id()));
      try {
        awaitAccountWaiter();
      } finally {
        release.countDown();
      }
      writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(reader.get(10, java.util.concurrent.TimeUnit.SECONDS))
          .isEqualTo(new SessionVersion(1));
    }
  }

  @Test
  void should_commit_hash_version_and_revocation_when_barrier_is_confirmed() {
    var user = registered();
    login(user);
    change.changePassword(command(user));
    assertThat(accounts.findByEmail(user.email()).orElseThrow().profile().sessionVersion())
        .isEqualTo(1);
    assertThat(barrier.find(user.id())).contains(new SessionVersion(1));
    assertThat(rows(user.id())).allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
  }

  @Test
  @DisplayName("Fallo compartido durante escritura impide confirmar contraseña")
  void should_roll_back_all_persistent_changes_when_publication_fails() {
    var user = registered();
    login(user);
    var before = rows(user.id());
    redis.opsForValue().set(RedisSessionVersions.PREFIX + user.id(), "corrupt");
    assertThatThrownBy(() -> change.changePassword(command(user)))
        .hasMessage("session-version-unavailable");
    assertUnchanged(user, before);
  }

  @Test
  void should_keep_applied_barrier_when_write_confirmation_is_lost() {
    var user = registered();
    login(user);
    var before = rows(user.id());
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IdentityException("session-version-unavailable");
            })
        .when(barrier)
        .publish(eq(user.id()), any());
    assertThatThrownBy(() -> change.changePassword(command(user)))
        .hasMessage("session-version-unavailable");
    assertUnchanged(user, before);
    assertThat(barrier.find(user.id())).contains(new SessionVersion(1));
  }

  @Test
  @DisplayName("Rollback posterior a la barrera no restaura sesiones")
  void should_keep_barrier_when_database_fails_before_commit() {
    var user = registered();
    login(user);
    var before = rows(user.id());
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              TransactionSynchronizationManager.registerSynchronization(
                  new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                      throw new DataAccessResourceFailureException(
                          "Injected commit availability failure");
                    }
                  });
              return null;
            })
        .when(barrier)
        .publish(eq(user.id()), any());
    assertThatThrownBy(() -> change.changePassword(command(user)))
        .hasMessage("session-version-unavailable");
    assertUnchanged(user, before);
    assertThat(barrier.find(user.id())).contains(new SessionVersion(1));
  }

  private void assertUnchanged(
      UserAccount user, java.util.List<java.util.Map<String, Object>> before) {
    assertThat(accounts.findByEmail(user.email())).contains(user);
    assertThat(rows(user.id())).isEqualTo(before);
  }

  private static PasswordChange command(UserAccount user) {
    return new PasswordChange(user.id(), PASSWORD, NEW_PASSWORD);
  }
}
