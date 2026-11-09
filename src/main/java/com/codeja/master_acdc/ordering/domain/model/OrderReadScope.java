package com.codeja.master_acdc.ordering.domain.model;

import java.util.Objects;
import java.util.UUID;

public sealed interface OrderReadScope {
  record Own(UUID customerId) implements OrderReadScope {
    public Own {
      Objects.requireNonNull(customerId, "customerId");
    }
  }

  enum Global implements OrderReadScope {
    INSTANCE
  }

  enum None implements OrderReadScope {
    INSTANCE
  }
}
