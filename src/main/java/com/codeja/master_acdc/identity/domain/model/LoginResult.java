package com.codeja.master_acdc.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public record LoginResult(AccessToken accessToken, String refreshToken, Instant refreshExpiresAt) {
  public LoginResult {
    Objects.requireNonNull(accessToken);
    Objects.requireNonNull(refreshToken);
    Objects.requireNonNull(refreshExpiresAt);
  }

  @Override
  public String toString() {
    return "LoginResult[redacted]";
  }
}
