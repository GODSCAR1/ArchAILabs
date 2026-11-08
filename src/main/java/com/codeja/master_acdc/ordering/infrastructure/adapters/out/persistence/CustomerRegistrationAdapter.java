package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.api.CustomerRegistrationFacade;
import java.util.UUID;

public final class CustomerRegistrationAdapter implements CustomerRegistrationFacade {
  private final CustomerJpaRepository customers;

  public CustomerRegistrationAdapter(CustomerJpaRepository customers) {
    this.customers = customers;
  }

  @Override
  public UUID register(String name) {
    if (name == null || name.isBlank() || name.length() > 120) {
      throw new IllegalArgumentException("Invalid customer name");
    }
    UUID id = UUID.randomUUID();
    customers.save(CustomerRegistrationPersistenceMapper.toEntity(id, name));
    return id;
  }
}
