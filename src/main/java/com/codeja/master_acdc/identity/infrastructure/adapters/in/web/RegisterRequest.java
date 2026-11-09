package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

public record RegisterRequest(String email, String password, String name) {
  @Override
  public String toString() {
    return "RegisterRequest[redacted]";
  }
}
