package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.RefundOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedRefundOrderUseCase implements RefundOrderUseCase {
  private final RefundOrderUseCase delegate;
  private final OrderActorResolver actor;

  public AuthorizedRefundOrderUseCase(RefundOrderUseCase delegate, OrderActorResolver actor) {
    this.delegate = delegate;
    this.actor = actor;
  }

  @Override
  @PreAuthorize("hasAuthority('order:refund')")
  public Order refund(UUID orderId) {
    actor.requireAccount();
    if (orderId == null) {
      throw new OrderNotFoundException(null);
    }
    return delegate.refund(orderId);
  }
}
