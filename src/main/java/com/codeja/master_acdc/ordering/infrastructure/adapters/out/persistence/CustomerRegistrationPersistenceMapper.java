package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import java.util.UUID;

public final class CustomerRegistrationPersistenceMapper {
  private CustomerRegistrationPersistenceMapper() {}

  public static CustomerJpaEntity toEntity(UUID id, String name) {
    return new CustomerJpaEntity(id, name);
  }
}
