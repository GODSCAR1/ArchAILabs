package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import java.util.Set;

public final class UserAccountPersistenceMapper {
  private UserAccountPersistenceMapper() {}

  public static UserAccountJpaEntity toEntity(UserAccount account) {
    var entity = new UserAccountJpaEntity();
    entity.id = account.id();
    entity.email = account.email();
    entity.passwordHash = account.passwordHash();
    entity.roles = account.profile().roles().stream().sorted().toArray(String[]::new);
    entity.enabled = account.profile().enabled();
    entity.sessionVersion = account.profile().sessionVersion();
    entity.customerId = account.profile().customerId();
    return entity;
  }

  public static UserAccount toDomain(UserAccountJpaEntity entity) {
    return new UserAccount(
        entity.id,
        entity.email,
        entity.passwordHash,
        new UserAccount.Profile(
            Set.of(entity.roles), entity.enabled, entity.sessionVersion, entity.customerId));
  }
}
