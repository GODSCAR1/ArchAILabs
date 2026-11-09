package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.ListOrdersByStatusUseCase;
import com.codeja.master_acdc.ordering.domain.model.OrderListingCriteria;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedListOrdersByStatusUseCase implements ListOrdersByStatusUseCase {
  private final ListOrdersByStatusUseCase delegate;
  private final OrderActorResolver actor;

  public AuthorizedListOrdersByStatusUseCase(
      ListOrdersByStatusUseCase delegate, OrderActorResolver actor) {
    this.delegate = delegate;
    this.actor = actor;
  }

  @Override
  @PreAuthorize("hasAnyAuthority('order:read-own','order:read-any')")
  public List<OrderSummary> list(OrderListingCriteria criteria) {
    return delegate.list(new OrderListingCriteria(criteria.status(), actor.readingScope()));
  }
}
