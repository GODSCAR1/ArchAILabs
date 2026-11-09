package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import java.util.Optional;
import java.util.UUID;

public final class UserAccountRepositoryAdapter implements UserAccounts {
  private final UserAccountJpaRepository accounts;
  private final jakarta.persistence.EntityManager entityManager;

  public UserAccountRepositoryAdapter(
      UserAccountJpaRepository accounts, jakarta.persistence.EntityManager entityManager) {
    this.accounts = accounts;
    this.entityManager = entityManager;
  }

  @Override
  public void save(UserAccount account) {
    accounts.saveAndFlush(UserAccountPersistenceMapper.toEntity(account));
  }

  @Override
  public Optional<UserAccount> findByEmail(String email) {
    return accounts.findByEmail(email).map(UserAccountPersistenceMapper::toDomain);
  }

  @Override
  public Optional<UserAccount> findForUpdate(UUID id) {
    return accounts
        .findForUpdate(id)
        .map(
            entity -> {
              // Login may have read the account before waiting for a concurrent password change.
              entityManager.refresh(entity);
              return UserAccountPersistenceMapper.toDomain(entity);
            });
  }
}
