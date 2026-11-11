package com.codeja.master_acdc.identity.domain.model;

public record PasswordResetConfirmation(String email, String token, String newPassword) {
  public PasswordResetConfirmation {
    email = new PasswordResetRequest(email).email();
    if (token == null || token.isBlank() || newPassword == null) {
      throw new IdentityException("invalid-password-reset-request");
    }
  }

  @Override
  public String toString() {
    return "PasswordResetConfirmation[redacted]";
  }
}
