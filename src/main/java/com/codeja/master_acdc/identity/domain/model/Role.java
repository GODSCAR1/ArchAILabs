package com.codeja.master_acdc.identity.domain.model;

import java.util.Set;

public enum Role {
  CUSTOMER(Set.of("order:create", "order:read-own", "order:cancel-own")),
  CATALOG_MANAGER(Set.of("product:write", "stock:reserve")),
  SUPPORT(Set.of("order:read-any", "order:refund")),
  ADMIN(Set.of("product:write", "stock:reserve", "order:read-any", "order:refund", "user:read"));

  private final Set<String> permissions;

  Role(Set<String> permissions) {
    this.permissions = Set.copyOf(permissions);
  }

  public Set<String> permissions() {
    return permissions;
  }
}
