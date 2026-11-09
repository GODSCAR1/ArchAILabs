package com.codeja.master_acdc.identity.domain.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record UserAccount(UUID id, String email, String passwordHash, Profile profile) {
  public UserAccount {
    Objects.requireNonNull(id);
    Objects.requireNonNull(email);
    Objects.requireNonNull(passwordHash);
    Objects.requireNonNull(profile);
    if (!email.equals(email.toLowerCase(java.util.Locale.ROOT)) || passwordHash.isBlank()) {
      throw new IllegalArgumentException("Invalid account");
    }
  }

  @Override
  public String toString() {
    return "UserAccount[id=" + id + "]";
  }

  public record Profile(Set<String> roles, boolean enabled, long sessionVersion, UUID customerId) {
    public Profile {
      roles = Set.copyOf(roles);
      if (roles.isEmpty() || sessionVersion < 0) {
        throw new IllegalArgumentException("Invalid profile");
      }
    }
  }
}
