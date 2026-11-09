package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.identity.application.port.RegisterUseCase;
import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.RefreshToken;
import com.codeja.master_acdc.identity.domain.model.Registration;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.config.TransactionalRegister;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class IdentityPersistenceIT extends IdentityIntegrationSupport {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RegisterUseCase register;
  @Autowired private UserAccounts accounts;
  @Autowired private RefreshTokens refreshes;
  @Autowired private PlatformTransactionManager manager;

  @Test
  void should_enforce_account_constraints_when_v8_is_applied() {
    String email = email();
    UUID customer = UUID.randomUUID();
    jdbc.update("insert into customers(id,name) values (?,?)", customer, "identity-customer");
    insertAccount(email, customer);
    assertThat(
            jdbc.queryForObject(
                "select session_version from user_account where email=?", Long.class, email))
        .isZero();
    assertThatThrownBy(() -> insertAccount(email, null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertAccount("UPPER@EXAMPLE.COM", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertAccount(email(), customer))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertAccount(email(), UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    insertAccount(email(), null);
    insertAccount(email(), null);
    assertThat(
            jdbc.queryForList(
                "select indexname from pg_indexes where tablename='refresh_token'", String.class))
        .contains(
            "refresh_token_family_id_idx",
            "refresh_token_user_id_idx",
            "refresh_token_token_hash_key");
  }

  @Test
  void should_round_trip_account_and_refresh_when_adapters_write_postgresql() {
    var registration = new Registration(email(), "Unique!12345X", "identity-mapped");
    UUID id = register.register(registration);
    var loaded = accounts.findByEmail(registration.email()).orElseThrow();
    assertThat(loaded.id()).isEqualTo(id);
    assertThat(loaded.passwordHash()).startsWith("{bcrypt}");
    assertThat(loaded.profile().customerId()).isNotNull();
    Instant now = Instant.parse("2026-11-08T12:00:00Z");
    var token =
        new RefreshToken(
            UUID.randomUUID(),
            new RefreshToken.Family(UUID.randomUUID(), id),
            "a".repeat(64),
            new RefreshToken.Lifetime(now, now.plusSeconds(604800), now.plusSeconds(2592000)),
            new RefreshToken.State(null, null));
    refreshes.save(token);
    var row = jdbc.queryForMap("select * from refresh_token where id=?", token.id());
    assertThat(row.get("token_hash")).isEqualTo(token.tokenHash());
    assertThat(row.get("family_expires_at")).isNotNull();
    assertThat(row.get("rotated_at")).isNull();
    assertThatThrownBy(
            () ->
                refreshes.save(
                    new RefreshToken(
                        UUID.randomUUID(),
                        token.family(),
                        token.tokenHash(),
                        token.lifetime(),
                        token.state())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void should_roll_back_customer_when_failure_follows_account_creation() {
    long customers = count("customers");
    long users = count("user_account");
    var failing =
        new TransactionalRegister(
            registration -> {
              register.register(registration);
              throw new IllegalStateException("forced-after-customer");
            },
            new TransactionTemplate(manager));
    assertThatThrownBy(
            () -> failing.register(new Registration(email(), "Unique!12345X", "identity-rollback")))
        .hasMessage("forced-after-customer");
    assertThat(count("customers")).isEqualTo(customers);
    assertThat(count("user_account")).isEqualTo(users);
  }

  @Test
  void should_commit_only_one_customer_when_normalized_registrations_compete() throws Exception {
    String email = email();
    long customers = count("customers");
    long users = count("user_account");
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> attempt(email, start));
      var second = executor.submit(() -> attempt(email.toUpperCase(java.util.Locale.ROOT), start));
      start.countDown();
      assertThat(
              java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("created", "email-already-registered");
    }
    assertThat(count("customers")).isEqualTo(customers + 1);
    assertThat(count("user_account")).isEqualTo(users + 1);
  }

  private String attempt(String email, CountDownLatch start) throws InterruptedException {
    start.await();
    try {
      register.register(new Registration(email, "Unique!12345X", "identity-concurrent"));
      return "created";
    } catch (IdentityException exception) {
      return exception.code();
    }
  }

  private void insertAccount(String email, UUID customer) {
    jdbc.update(
        "insert into user_account(id,email,password_hash,roles,enabled,customer_id) values"
            + " (?,?,?,ARRAY['ADMIN'],true,?)",
        UUID.randomUUID(),
        email,
        "hash",
        customer);
  }

  private long count(String table) {
    return switch (table) {
      case "customers" -> jdbc.queryForObject("select count(*) from customers", Long.class);
      case "user_account" -> jdbc.queryForObject("select count(*) from user_account", Long.class);
      default -> throw new IllegalArgumentException("Unknown table");
    };
  }

  private static String email() {
    return UUID.randomUUID() + "@example.com";
  }
}
