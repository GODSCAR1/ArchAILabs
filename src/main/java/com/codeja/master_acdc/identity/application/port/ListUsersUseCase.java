package com.codeja.master_acdc.identity.application.port;

import com.codeja.master_acdc.identity.domain.model.UserPage;
import com.codeja.master_acdc.identity.domain.model.UserPageRequest;

public interface ListUsersUseCase {
  UserPage list(UserPageRequest request);
}
