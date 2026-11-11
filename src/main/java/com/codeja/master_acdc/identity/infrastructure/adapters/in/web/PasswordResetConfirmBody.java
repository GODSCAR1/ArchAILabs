package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

public record PasswordResetConfirmBody(String email, String token, String newPassword) {
  @Override
  public String toString() {
    return "PasswordResetConfirmBody[redacted]";
  }
}
