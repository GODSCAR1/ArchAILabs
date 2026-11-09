package com.codeja.master_acdc.identity.domain.port;

import com.codeja.master_acdc.identity.domain.model.AccessToken;
import com.codeja.master_acdc.identity.domain.model.UserAccount;

public interface AccessTokenIssuer {
  AccessToken issue(UserAccount account);
}
