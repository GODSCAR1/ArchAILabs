package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshTokenJpaEntity {
  @Id UUID id;

  @Column(name = "family_id", nullable = false)
  UUID familyId;

  @Column(name = "user_id", nullable = false)
  UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "char(64)")
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  String tokenHash;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  Instant expiresAt;

  @Column(name = "family_expires_at", nullable = false)
  Instant familyExpiresAt;

  @Column(name = "rotated_at")
  Instant rotatedAt;

  @Column(name = "revoked_at")
  Instant revokedAt;

  protected RefreshTokenJpaEntity() {}
}
