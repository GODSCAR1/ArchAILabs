package com.codeja.master_acdc.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RefreshToken(
    UUID id, Family family, String tokenHash, Lifetime lifetime, State state) {
  public RefreshToken {
    Objects.requireNonNull(id);
    Objects.requireNonNull(family);
    Objects.requireNonNull(lifetime);
    Objects.requireNonNull(state);
    if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid refresh hash");
    }
  }

  public record Family(UUID id, UUID userId) {
    public Family {
      Objects.requireNonNull(id);
      Objects.requireNonNull(userId);
    }
  }

  public record Lifetime(Instant createdAt, Instant expiresAt, Instant familyExpiresAt) {
    public Lifetime {
      Objects.requireNonNull(createdAt);
      Objects.requireNonNull(expiresAt);
      Objects.requireNonNull(familyExpiresAt);
      if (!createdAt.isBefore(expiresAt) || expiresAt.isAfter(familyExpiresAt)) {
        throw new IllegalArgumentException("Invalid refresh lifetime");
      }
    }
  }

  public record State(Instant rotatedAt, Instant revokedAt) {}
}
