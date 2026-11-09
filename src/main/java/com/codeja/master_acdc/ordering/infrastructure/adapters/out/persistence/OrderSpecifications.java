package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

final class OrderSpecifications {
  private OrderSpecifications() {}

  static Specification<OrderJpaEntity> hasStatus(OrderStatus status) {
    return (root, query, builder) -> builder.equal(root.get(OrderJpaEntity_.status), status.name());
  }

  static Specification<OrderJpaEntity> visibleTo(OrderReadScope scope) {
    return (root, query, builder) ->
        switch (scope) {
          case OrderReadScope.Own own ->
              builder.equal(root.get(OrderJpaEntity_.customerId), own.customerId());
          case OrderReadScope.Global ignored -> builder.conjunction();
          case OrderReadScope.None ignored -> builder.disjunction();
        };
  }
}
