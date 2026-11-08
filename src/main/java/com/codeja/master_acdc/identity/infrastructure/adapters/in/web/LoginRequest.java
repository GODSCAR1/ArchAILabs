package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

public record LoginRequest(String email, String password) {
  @Override
  public String toString() {
    return "LoginRequest[redacted]";
  }
}
