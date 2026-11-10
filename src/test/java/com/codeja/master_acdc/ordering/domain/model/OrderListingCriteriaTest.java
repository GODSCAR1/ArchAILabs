package com.codeja.master_acdc.ordering.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderListingCriteriaTest {
  @Test
  void should_require_explicit_status_and_scope_when_constructing_criteria() {
    var criteria =
        new OrderListingCriteria(OrderStatus.PAID, OrderReadScope.Global.INSTANCE, "id ASC");
    assertThat(criteria.status()).isEqualTo(OrderStatus.PAID);
    assertThat(criteria.scope()).isEqualTo(OrderReadScope.Global.INSTANCE);
    assertThatThrownBy(
            () -> new OrderListingCriteria(null, OrderReadScope.Global.INSTANCE, "id ASC"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new OrderListingCriteria(OrderStatus.PAID, null, "id ASC"))
        .isInstanceOf(NullPointerException.class);
  }
}
