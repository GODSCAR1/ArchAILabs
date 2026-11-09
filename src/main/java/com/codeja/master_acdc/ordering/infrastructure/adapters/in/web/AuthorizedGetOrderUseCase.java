package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.GetOrderUseCase;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedGetOrderUseCase implements GetOrderUseCase {
  private final GetOrderUseCase delegate;
  private final OrderActorResolver actor;

  public AuthorizedGetOrderUseCase(GetOrderUseCase delegate, OrderActorResolver actor) {
    this.delegate = delegate;
    this.actor = actor;
  }

  @Override
  @PreAuthorize("hasAnyAuthority('order:read-own','order:read-any')")
  public Order get(UUID orderId, OrderReadScope ignoredScope) {
    return delegate.get(orderId, actor.readingScope());
  }
}
