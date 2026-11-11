package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

public record PasswordResetRequestBody(String email) {
  @Override
  public String toString() {
    return "PasswordResetRequestBody[redacted]";
  }
}
