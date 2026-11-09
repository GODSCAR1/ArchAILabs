package com.codeja.master_acdc.identity;

import com.codeja.master_acdc.identity.application.port.ChangePasswordUseCase;
import com.codeja.master_acdc.identity.application.port.LoginUseCase;
import com.codeja.master_acdc.identity.application.port.LogoutUseCase;
import com.codeja.master_acdc.identity.application.port.RefreshUseCase;
import com.codeja.master_acdc.identity.application.port.RegisterUseCase;
import com.codeja.master_acdc.identity.domain.model.Login;
import com.codeja.master_acdc.identity.domain.model.LoginResult;
import com.codeja.master_acdc.identity.domain.model.Registration;
import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.RefreshSecretGenerator;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.config.IdentityTransactions;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class SessionIntegrationSupport extends IdentityIntegrationSupport {
  static final String PASSWORD = "Unique!12345X";
  static final String NEW_PASSWORD = "Unique!67890Z";
  @Autowired RegisterUseCase register;
  @Autowired LoginUseCase login;
  @Autowired RefreshUseCase refresh;
  @Autowired LogoutUseCase logout;
  @Autowired ChangePasswordUseCase change;
  @Autowired UserAccounts accounts;
  @Autowired RefreshSecretGenerator secrets;
  @Autowired IdentityTransactions transactions;
  @Autowired JdbcTemplate jdbc;

  UserAccount registered() {
    String email = UUID.randomUUID() + "@example.com";
    register.register(new Registration(email, PASSWORD, "session-it"));
    return accounts.findByEmail(email).orElseThrow();
  }

  LoginResult login(UserAccount account) {
    return login.login(new Login(account.email(), PASSWORD));
  }

  java.util.List<java.util.Map<String, Object>> rows(UUID userId) {
    return jdbc.queryForList("select * from refresh_token where user_id=? order by id", userId);
  }

  static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Barrier timed out");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Barrier interrupted", exception);
    }
  }

  void awaitAccountWaiter() {
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(5))
        .until(
            () ->
                jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where datname=current_database() and"
                            + " wait_event_type='Lock' and query like '%user_account%' and"
                            + " pid<>pg_backend_pid()",
                        Long.class)
                    > 0);
  }
}
