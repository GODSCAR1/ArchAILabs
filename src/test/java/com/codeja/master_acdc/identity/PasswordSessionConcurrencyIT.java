package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.domain.model.PasswordChange;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PasswordSessionConcurrencyIT extends SessionIntegrationSupport {
  @Test
  void should_reject_refresh_after_password_commit_when_password_holds_account_first()
      throws Exception {
    var user = registered();
    var session = login(user);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var password =
          executor.submit(
              () ->
                  transactions.execute(
                      () -> {
                        accounts.findForUpdate(user.id()).orElseThrow();
                        locked.countDown();
                        await(release);
                        change.changePassword(
                            new PasswordChange(user.id(), PASSWORD, NEW_PASSWORD));
                        return null;
                      }));
      await(locked);
      var refreshing = executor.submit(() -> refresh.refresh(session.refreshToken()));
      try {
        awaitAccountWaiter();
      } finally {
        release.countDown();
      }
      password.get(10, TimeUnit.SECONDS);
      assertThat(refreshing.get(10, TimeUnit.SECONDS).accepted()).isFalse();
      assertThat(rows(user.id()))
          .hasSize(1)
          .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
    }
  }

  @Test
  void should_revoke_rotated_successor_when_refresh_holds_account_before_password_change()
      throws Exception {
    var user = registered();
    var session = login(user);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var refreshing =
          executor.submit(
              () ->
                  transactions.execute(
                      () -> {
                        var result = refresh.refresh(session.refreshToken());
                        locked.countDown();
                        await(release);
                        return result;
                      }));
      await(locked);
      var password =
          executor.submit(
              () -> change.changePassword(new PasswordChange(user.id(), PASSWORD, NEW_PASSWORD)));
      try {
        awaitAccountWaiter();
      } finally {
        release.countDown();
      }
      var result = refreshing.get(10, TimeUnit.SECONDS);
      password.get(10, TimeUnit.SECONDS);
      assertThat(result.accepted()).isTrue();
      assertThat(refresh.refresh(result.tokens().orElseThrow().refreshToken()).accepted())
          .isFalse();
      assertThat(rows(user.id()))
          .hasSize(2)
          .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
    }
  }

  @Test
  void should_reject_stale_login_when_password_changed_after_initial_account_read()
      throws Exception {
    var user = registered();
    var read = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var stale = executor.submit(() -> staleLogin(user, read, release));
      await(read);
      try {
        change.changePassword(new PasswordChange(user.id(), PASSWORD, NEW_PASSWORD));
      } finally {
        release.countDown();
      }
      assertThat(stale.get(10, TimeUnit.SECONDS)).isEqualTo("invalid-credentials");
      assertThat(rows(user.id())).isEmpty();
    }
  }

  private String staleLogin(
      com.codeja.master_acdc.identity.domain.model.UserAccount user,
      CountDownLatch read,
      CountDownLatch release) {
    try {
      transactions.execute(
          () -> {
            accounts.findByEmail(user.email()).orElseThrow();
            read.countDown();
            await(release);
            return login(user);
          });
      return "accepted";
    } catch (com.codeja.master_acdc.identity.domain.model.IdentityException exception) {
      return exception.code();
    }
  }
}
