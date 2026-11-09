package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.UserPage;
import com.codeja.master_acdc.identity.domain.model.UserPageRequest;
import org.springframework.util.MultiValueMap;

public final class AdminUserWebMapper {
  private AdminUserWebMapper() {}

  public static UserPageRequest request(MultiValueMap<String, String> params) {
    if (params.containsKey("sort")) {
      throw new IdentityException("invalid-user-page");
    }
    return new UserPageRequest(number(params, "page", 0), number(params, "size", 20));
  }

  private static int number(MultiValueMap<String, String> params, String key, int fallback) {
    if (!params.containsKey(key)) {
      return fallback;
    }
    if (params.get(key).size() != 1) {
      throw new IdentityException("invalid-user-page");
    }
    try {
      return Integer.parseInt(params.getFirst(key));
    } catch (NumberFormatException exception) {
      throw new IdentityException("invalid-user-page");
    }
  }

  public static AdminUsersResponse response(UserPage page) {
    return new AdminUsersResponse(
        page.content().stream()
            .map(
                user ->
                    new AdminUsersResponse.User(
                        user.id(), user.email(), user.roles(), user.enabled()))
            .toList(),
        page.request().page(),
        page.request().size(),
        page.totalElements(),
        page.totalPages());
  }
}
