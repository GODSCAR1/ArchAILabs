package com.codeja.master_acdc.ordering.domain.model;

import java.util.Objects;

public record OrderListingCriteria(OrderStatus status, OrderReadScope scope, String sort) {
  public OrderListingCriteria {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(sort, "sort");
  }
}
