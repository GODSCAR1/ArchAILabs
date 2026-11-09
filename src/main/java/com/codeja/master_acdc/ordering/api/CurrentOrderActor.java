package com.codeja.master_acdc.ordering.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Current persisted account; empty means absent or invalid subject, null customer means unlinked.
 */
public interface CurrentOrderActor {
  Optional<Actor> current();

  record Actor(UUID accountId, UUID customerId) {
    public Actor {
      Objects.requireNonNull(accountId, "accountId");
    }
  }
}
