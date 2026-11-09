package com.codeja.master_acdc.identity.domain.model;

import java.util.Objects;

public record RefreshSecret(String value, String hash) {
  public RefreshSecret {
    Objects.requireNonNull(value);
    if (hash == null || !hash.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid token hash");
    }
  }

  @Override
  public String toString() {
    return "RefreshSecret[redacted]";
  }
}
