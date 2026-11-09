package com.codeja.master_acdc.ordering.domain.model;

import java.util.Objects;

public record OrderListingCriteria(OrderStatus status, OrderReadScope scope) {
  public OrderListingCriteria {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(scope, "scope");
  }
}
