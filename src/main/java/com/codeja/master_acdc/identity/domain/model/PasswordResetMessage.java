package com.codeja.master_acdc.identity.domain.model;

import java.util.Objects;

public record PasswordResetMessage(String email, String token) {
  public PasswordResetMessage {
    Objects.requireNonNull(email);
    Objects.requireNonNull(token);
  }

  @Override
  public String toString() {
    return "PasswordResetMessage[redacted]";
  }
}
