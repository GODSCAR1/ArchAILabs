package com.codeja.master_acdc.identity.domain.model;

import java.util.Objects;

public record AccessToken(String value, long expiresIn) {
  public AccessToken {
    Objects.requireNonNull(value);
    if (expiresIn <= 0) {
      throw new IllegalArgumentException("Invalid token lifetime");
    }
  }

  @Override
  public String toString() {
    return "AccessToken[redacted]";
  }
}
