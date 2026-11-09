package com.codeja.master_acdc.identity.domain.model;

public record Login(String email, String password) {
  public Login {
    email = email == null ? "" : email.toLowerCase(java.util.Locale.ROOT);
  }

  @Override
  public String toString() {
    return "Login[redacted]";
  }
}
