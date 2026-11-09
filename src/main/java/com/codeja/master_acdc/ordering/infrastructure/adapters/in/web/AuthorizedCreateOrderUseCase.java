package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.RequestedOrderLine;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedCreateOrderUseCase implements CreateOrderUseCase {
  private final CreateOrderUseCase delegate;
  private final OrderActorResolver actor;

  public AuthorizedCreateOrderUseCase(CreateOrderUseCase delegate, OrderActorResolver actor) {
    this.delegate = delegate;
    this.actor = actor;
  }

  @Override
  @PreAuthorize("hasAuthority('order:create')")
  public Order create(UUID ignoredCustomer, List<RequestedOrderLine> lines) {
    return delegate.create(actor.creationCustomer(), lines);
  }
}
