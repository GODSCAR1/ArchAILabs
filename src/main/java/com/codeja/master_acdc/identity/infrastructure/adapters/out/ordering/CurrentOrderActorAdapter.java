package com.codeja.master_acdc.identity.infrastructure.adapters.out.ordering;

import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.OrderActorPersistenceMapper;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.UserAccountJpaRepository;
import com.codeja.master_acdc.ordering.api.CurrentOrderActor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentOrderActorAdapter implements CurrentOrderActor {
  private final UserAccountJpaRepository accounts;

  public CurrentOrderActorAdapter(UserAccountJpaRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public Optional<Actor> current() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }
    UUID id;
    try {
      id = UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
    return accounts.findCustomerAssociationById(id).map(OrderActorPersistenceMapper::toActor);
  }
}
