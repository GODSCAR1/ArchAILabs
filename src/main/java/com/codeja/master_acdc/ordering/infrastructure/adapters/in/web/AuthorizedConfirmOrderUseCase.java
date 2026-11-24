package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.ConfirmOrderCommand;
import com.codeja.master_acdc.ordering.application.port.ConfirmOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.OrderDetail;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedConfirmOrderUseCase implements ConfirmOrderUseCase {
  private final ConfirmOrderUseCase delegate;
  private final OrderActorResolver actor;

  public AuthorizedConfirmOrderUseCase(ConfirmOrderUseCase delegate, OrderActorResolver actor) {
    this.delegate = delegate;
    this.actor = actor;
  }

  @Override
  @PreAuthorize("hasAuthority('order:confirm-own')")
  public OrderDetail confirm(ConfirmOrderCommand command) {
    UUID orderId = command.orderId();
    UUID customer = actor.cancellationCustomer(orderId);
    if (orderId == null) {
      throw new OrderNotFoundException(null);
    }
    return delegate.confirm(new ConfirmOrderCommand(orderId, customer));
  }
}
