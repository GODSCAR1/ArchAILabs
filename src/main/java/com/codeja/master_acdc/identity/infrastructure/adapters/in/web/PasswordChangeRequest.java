package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

public record PasswordChangeRequest(String currentPassword, String newPassword) {
  @Override
  public String toString() {
    return "PasswordChangeRequest[redacted]";
  }
}
