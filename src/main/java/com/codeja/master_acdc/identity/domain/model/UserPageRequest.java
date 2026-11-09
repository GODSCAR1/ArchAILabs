package com.codeja.master_acdc.identity.domain.model;

public record UserPageRequest(int page, int size) {
  public UserPageRequest {
    if (page < 0 || size < 1 || size > 100) {
      throw new IdentityException("invalid-user-page");
    }
  }
}
