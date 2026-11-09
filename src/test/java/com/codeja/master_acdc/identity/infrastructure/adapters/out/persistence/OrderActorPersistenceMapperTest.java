package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderActorPersistenceMapperTest {
  @Test
  void should_preserve_identity_and_nullable_customer_when_mapping_projection() {
    UUID id = UUID.randomUUID();
    UUID customer = UUID.randomUUID();
    assertThat(OrderActorPersistenceMapper.toActor(new AccountCustomerRow(id, customer)))
        .satisfies(
            actor -> {
              assertThat(actor.accountId()).isEqualTo(id);
              assertThat(actor.customerId()).isEqualTo(customer);
            });
    assertThat(OrderActorPersistenceMapper.toActor(new AccountCustomerRow(id, null)).customerId())
        .isNull();
  }
}
