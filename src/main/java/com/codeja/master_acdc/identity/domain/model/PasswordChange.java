package com.codeja.master_acdc.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PasswordChange(UUID userId, String currentPassword, String newPassword) {
  public PasswordChange {
    Objects.requireNonNull(userId);
  }

  @Override
  public String toString() {
    return "PasswordChange[redacted]";
  }
}
