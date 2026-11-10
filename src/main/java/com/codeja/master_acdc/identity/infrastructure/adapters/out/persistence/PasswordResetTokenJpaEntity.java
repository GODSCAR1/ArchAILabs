package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetTokenJpaEntity {
  @Id
  @Column(name = "user_id")
  UUID userId;

  @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)")
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  String tokenHash;

  @Column(name = "expires_at", nullable = false)
  Instant expiresAt;

  protected PasswordResetTokenJpaEntity() {}
}
