package com.codeja.master_acdc.ordering.domain.model;

import com.codeja.master_acdc.ordering.domain.exception.InvalidOrderSortException;
import java.util.Objects;

public record OrderListingCriteria(OrderStatus status, OrderReadScope scope, String sort) {
  public OrderListingCriteria {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(sort, "sort");
    sort = sort.strip();
    if (!sort.matches("(id|customerName|lineCount|totalUnits)(\\s+(ASC|DESC))?")) {
      throw new InvalidOrderSortException();
    }
  }
}
