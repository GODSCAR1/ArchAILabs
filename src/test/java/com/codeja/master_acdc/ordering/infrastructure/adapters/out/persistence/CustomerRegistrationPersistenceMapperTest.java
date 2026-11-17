package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.ordering.domain.model.CustomerName;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerRegistrationPersistenceMapperTest {
  @Test
  void should_preserve_customer_fields_when_mapping_registration() {
    UUID id = UUID.randomUUID();
    var entity =
        CustomerRegistrationPersistenceMapper.toEntity(
            id, "Customer", new CustomerName("Customer", null));
    assertThat(entity.id()).isEqualTo(id);
    assertThat(entity.name()).isEqualTo("Customer");
    assertThat(entity.firstName()).isEqualTo("Customer");
    assertThat(entity.lastName()).isNull();
  }
}
