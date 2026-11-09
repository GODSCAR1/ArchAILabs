package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import com.codeja.master_acdc.identity.application.port.ListUsersUseCase;
import com.codeja.master_acdc.identity.domain.model.UserPage;
import com.codeja.master_acdc.identity.domain.model.UserPageRequest;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedListUsersUseCase implements ListUsersUseCase {
  private final ListUsersUseCase delegate;

  public AuthorizedListUsersUseCase(ListUsersUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @PreAuthorize("hasAuthority('user:read')")
  public UserPage list(UserPageRequest request) {
    return delegate.list(request);
  }
}
