package com.codeja.master_acdc.identity.application;

import com.codeja.master_acdc.identity.application.port.ListUsersUseCase;
import com.codeja.master_acdc.identity.domain.model.UserPage;
import com.codeja.master_acdc.identity.domain.model.UserPageRequest;
import com.codeja.master_acdc.identity.domain.port.UserSummaryQuery;

public class ListUsersService implements ListUsersUseCase {
  private final UserSummaryQuery users;

  public ListUsersService(UserSummaryQuery users) {
    this.users = users;
  }

  @Override
  public UserPage list(UserPageRequest request) {
    return users.list(request);
  }
}
