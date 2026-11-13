package com.codeja.master_acdc.identity.domain.model;

public record SessionVersion(long value) {
  public SessionVersion {
    if (value < 0) {
      throw new IllegalArgumentException("Negative session version");
    }
  }

  public SessionVersion next() {
    try {
      return new SessionVersion(Math.incrementExact(value));
    } catch (ArithmeticException exception) {
      throw new IdentityException("session-version-unavailable", exception);
    }
  }
}
