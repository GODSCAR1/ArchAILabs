package com.codeja.master_acdc.identity.infrastructure.adapters.out.ordering;

import com.codeja.master_acdc.identity.domain.port.CustomerCreator;
import com.codeja.master_acdc.ordering.api.CustomerRegistrationFacade;
import java.util.UUID;

public final class CustomerCreatorAdapter implements CustomerCreator {
  private final CustomerRegistrationFacade customers;

  public CustomerCreatorAdapter(CustomerRegistrationFacade customers) {
    this.customers = customers;
  }

  @Override
  public UUID create(String name) {
    return customers.register(name);
  }
}
