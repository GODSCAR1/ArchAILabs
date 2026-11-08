package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** Escenarios heredados por AuthFlowIT para mantener acotado el archivo del flujo. */
abstract class AuthHttpConcurrencyScenarios extends AuthHttpSupport {
  @Autowired private DataSource dataSource;
  private List<List<java.util.Map<String, Object>>> businessBefore;

  @BeforeEach
  void snapshot_business_state() {
    businessBefore = businessState();
  }

  @AfterEach
  void assert_business_state_is_unchanged() {
    assertThat(businessState()).isEqualTo(businessBefore);
  }

  private List<List<java.util.Map<String, Object>>> businessState() {
    return List.of(
        jdbc.queryForList("select * from orders order by id"),
        jdbc.queryForList("select * from stock order by product_id"));
  }

  @Test
  @DisplayName("Dos refresh simultáneos tienen un solo ganador")
  void should_return_one_success_and_one_rejection_when_http_refreshes_race() throws Exception {
    var browser = session();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<MvcResult> request =
          () -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return mvc.perform(cookiePost("/api/auth/refresh", browser)).andReturn();
          };
      var first = executor.submit(request);
      var second = executor.submit(request);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      assertThat(results)
          .extracting(result -> result.getResponse().getStatus())
          .containsExactlyInAnyOrder(200, 401);
      var winner =
          results.stream()
              .filter(result -> result.getResponse().getStatus() == 200)
              .findFirst()
              .orElseThrow();
      var successor =
          new Browser(
              browser.user(),
              winner.getResponse().getCookie("refresh_token"),
              browser.csrf(),
              browser.access());
      problem(cookiePost("/api/auth/refresh", successor), 401, "invalid-refresh-token");
      assertThat(tokens(browser.user()))
          .hasSize(2)
          .allSatisfy(row -> assertThat(row.get("revoked_at")).isNotNull());
    }
  }

  @Test
  @DisplayName("Familia ocupada no produce rotación parcial")
  void should_return_retry_after_and_roll_back_when_refresh_lock_times_out() throws Exception {
    var browser = session();
    var before = tokens(browser.user());
    try (var connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement("select id from user_account where id=? for update")) {
      connection.setAutoCommit(false);
      statement.setObject(1, browser.user());
      statement.executeQuery().close();
      problem(cookiePost("/api/auth/refresh", browser), 503, "authentication-busy")
          .andExpect(header().string("Retry-After", "1"));
      connection.rollback();
    }
    assertThat(tokens(browser.user())).isEqualTo(before);
    rotate(browser);
  }
}
