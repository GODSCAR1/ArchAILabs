package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.api.CurrentOrderActor;

public final class OrderActorPersistenceMapper {
  private OrderActorPersistenceMapper() {}

  public static CurrentOrderActor.Actor toActor(AccountCustomerRow row) {
    return new CurrentOrderActor.Actor(row.id(), row.customerId());
  }
}
