package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerRegistrationPersistenceMapperTest {
  @Test
  void should_preserve_customer_fields_when_mapping_registration() {
    UUID id = UUID.randomUUID();
    var entity = CustomerRegistrationPersistenceMapper.toEntity(id, "Customer");
    assertThat(entity.id()).isEqualTo(id);
    assertThat(entity.name()).isEqualTo("Customer");
  }
}
