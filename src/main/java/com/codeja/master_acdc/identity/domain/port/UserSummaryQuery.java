package com.codeja.master_acdc.identity.domain.port;

import com.codeja.master_acdc.identity.domain.model.UserPage;
import com.codeja.master_acdc.identity.domain.model.UserPageRequest;

public interface UserSummaryQuery {
  UserPage list(UserPageRequest request);
}
