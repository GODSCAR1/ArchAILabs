package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.api.CurrentOrderActor;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class OrderActorResolver {
  private final CurrentOrderActor currentActor;

  public OrderActorResolver(CurrentOrderActor currentActor) {
    this.currentActor = currentActor;
  }

  public UUID creationCustomer() {
    UUID customerId = account().customerId();
    if (customerId == null) {
      throw new AccessDeniedException("forbidden");
    }
    return customerId;
  }

  public UUID cancellationCustomer(UUID orderId) {
    UUID customerId = account().customerId();
    if (customerId == null) {
      throw new OrderNotFoundException(orderId);
    }
    return customerId;
  }

  public OrderReadScope readingScope() {
    var actor = account();
    boolean global =
        SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("order:read-any"));
    if (global) {
      return OrderReadScope.Global.INSTANCE;
    }
    return actor.customerId() == null
        ? OrderReadScope.None.INSTANCE
        : new OrderReadScope.Own(actor.customerId());
  }

  private CurrentOrderActor.Actor account() {
    return currentActor.current().orElseThrow(() -> new AccessDeniedException("forbidden"));
  }
}
