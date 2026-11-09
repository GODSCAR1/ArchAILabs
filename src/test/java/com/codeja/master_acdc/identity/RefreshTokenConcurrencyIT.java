package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenConcurrencyIT extends SessionIntegrationSupport {
  @Test
  @DisplayName("Dos refresh simultáneos tienen un solo ganador")
  void should_commit_one_rotation_and_revoke_successor_when_two_independent_requests_compete()
      throws Exception {
    assertThat(
            org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive())
        .isFalse();
    var user = registered();
    var session = login(user);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<com.codeja.master_acdc.identity.domain.model.RefreshResult>
          request =
              () -> {
                ready.countDown();
                await(start);
                return refresh.refresh(session.refreshToken());
              };
      var first = executor.submit(request);
      var second = executor.submit(request);
      await(ready);
      start.countDown();
      var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      assertThat(results).filteredOn(result -> result.accepted()).hasSize(1);
      assertThat(results).filteredOn(result -> !result.accepted()).hasSize(1);
      var successor =
          results.stream().flatMap(result -> result.tokens().stream()).findFirst().orElseThrow();
      assertThat(rows(user.id()))
          .hasSize(2)
          .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
      assertThat(refresh.refresh(successor.refreshToken()).accepted()).isFalse();
    }
  }
}
