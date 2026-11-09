package com.codeja.master_acdc.identity.domain.model;

import java.util.Set;
import java.util.UUID;

public record UserSummary(UUID id, String email, Set<String> roles, boolean enabled) {
  public UserSummary {
    roles = Set.copyOf(roles);
  }
}
