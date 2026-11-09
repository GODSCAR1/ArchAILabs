package com.codeja.master_acdc.identity.domain.port;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserAccounts {
  void save(UserAccount account);

  Optional<UserAccount> findByEmail(String email);

  Optional<UserAccount> findForUpdate(UUID id);
}
