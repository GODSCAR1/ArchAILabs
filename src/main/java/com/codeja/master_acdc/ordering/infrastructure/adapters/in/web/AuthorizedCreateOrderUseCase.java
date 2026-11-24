package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.OrderDetail;
import com.codeja.master_acdc.ordering.application.port.PlaceOrderCommand;
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
  public OrderDetail create(PlaceOrderCommand command) {
    return delegate.create(new PlaceOrderCommand(actor.creationCustomer(), command.lines()));
  }
}
