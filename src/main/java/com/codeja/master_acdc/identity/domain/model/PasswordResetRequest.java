package com.codeja.master_acdc.identity.domain.model;

public record PasswordResetRequest(String email) {
  public PasswordResetRequest {
    try {
      email = new Registration(email, "", null).email();
    } catch (IdentityException exception) {
      throw new IdentityException("invalid-password-reset-request", exception);
    }
  }

  @Override
  public String toString() {
    return "PasswordResetRequest[redacted]";
  }
}
