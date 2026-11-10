package com.codeja.master_acdc.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PasswordResetToken(UUID userId, String hash, Instant expiresAt) {
  public PasswordResetToken {
    Objects.requireNonNull(userId);
    Objects.requireNonNull(hash);
    Objects.requireNonNull(expiresAt);
    if (hash.isBlank()) {
      throw new IllegalArgumentException("Invalid token hash");
    }
  }

  @Override
  public String toString() {
    return "PasswordResetToken[redacted]";
  }
}
