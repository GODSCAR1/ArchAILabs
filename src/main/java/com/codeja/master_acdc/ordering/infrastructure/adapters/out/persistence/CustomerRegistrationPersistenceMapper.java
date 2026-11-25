package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.CustomerName;
import java.util.UUID;

final class CustomerRegistrationPersistenceMapper {
  private CustomerRegistrationPersistenceMapper() {}

  public static CustomerJpaEntity toEntity(UUID id, String name, CustomerName parts) {
    return new CustomerJpaEntity(id, name, parts.firstName(), parts.lastName());
  }
}
