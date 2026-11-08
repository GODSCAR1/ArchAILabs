package com.codeja.master_acdc.identity.domain.model;

public final class IdentityException extends RuntimeException {
  private final String code;

  public IdentityException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
