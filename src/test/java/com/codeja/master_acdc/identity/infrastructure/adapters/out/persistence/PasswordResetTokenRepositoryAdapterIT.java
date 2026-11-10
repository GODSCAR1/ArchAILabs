package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.identity.IdentityIntegrationSupport;
import com.codeja.master_acdc.identity.domain.model.PasswordResetToken;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PasswordResetTokenRepositoryAdapterIT extends IdentityIntegrationSupport {
  @Autowired private PasswordResetTokenJpaRepository repository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  @Test
  void should_replace_and_consume_token_when_account_exists() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into user_account(id,email,password_hash,roles,enabled,session_version) values (?,"
            + " ?, 'hash', ARRAY['CUSTOMER'], true, 0)",
        id,
        id + "@example.com");
    var adapter = new PasswordResetTokenRepositoryAdapter(repository);
    var first = new PasswordResetToken(id, "a".repeat(64), Instant.parse("2026-01-01T00:15:00Z"));
    var second = new PasswordResetToken(id, "b".repeat(64), Instant.parse("2026-01-01T00:30:00Z"));
    adapter.save(first);
    entityManager.clear();
    assertThat(adapter.findByUserId(id)).contains(first);
    adapter.save(second);
    entityManager.clear();
    assertThat(adapter.findByUserId(id)).contains(second);
    adapter.deleteByUserId(id);
    repository.flush();
    entityManager.clear();
    assertThat(adapter.findByUserId(id)).isEmpty();
  }
}
