package com.codeja.master_acdc.identity.domain.model;

import java.util.Objects;
import java.util.Optional;

/** Un rechazo permite confirmar la revocación antes de traducirlo a un error de transporte. */
public record RefreshResult(Optional<LoginResult> tokens) {
  public RefreshResult {
    Objects.requireNonNull(tokens);
  }

  public static RefreshResult rejected() {
    return new RefreshResult(Optional.empty());
  }

  public static RefreshResult issued(LoginResult tokens) {
    return new RefreshResult(Optional.of(tokens));
  }

  public boolean accepted() {
    return tokens.isPresent();
  }

  @Override
  public String toString() {
    return "RefreshResult[accepted=" + accepted() + "]";
  }
}
