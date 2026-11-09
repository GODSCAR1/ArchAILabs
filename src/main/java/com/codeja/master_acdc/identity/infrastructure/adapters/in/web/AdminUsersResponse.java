package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AdminUsersResponse(
    List<User> content, int page, int size, long totalElements, long totalPages) {
  public record User(UUID id, String email, Set<String> roles, boolean enabled) {}
}
