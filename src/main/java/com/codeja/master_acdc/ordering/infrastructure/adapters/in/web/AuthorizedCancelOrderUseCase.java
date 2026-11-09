package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedCancelOrderUseCase implements CancelOrderUseCase {
  private final CancelOrderUseCase delegate;
  private final OrderActorResolver actor;

  public AuthorizedCancelOrderUseCase(CancelOrderUseCase delegate, OrderActorResolver actor) {
    this.delegate = delegate;
    this.actor = actor;
  }

  @Override
  @PreAuthorize("hasAuthority('order:cancel-own')")
  public Order cancel(UUID orderId, UUID ignoredCustomer) {
    UUID customer = actor.cancellationCustomer(orderId);
    if (orderId == null) {
      throw new OrderNotFoundException(null);
    }
    return delegate.cancel(orderId, customer);
  }
}
