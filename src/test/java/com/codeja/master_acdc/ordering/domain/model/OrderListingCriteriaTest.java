package com.codeja.master_acdc.ordering.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.ordering.domain.exception.InvalidOrderSortException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderListingCriteriaTest {
  @Test
  @DisplayName("Campo dirección o fragmento no permitido")
  void should_reject_sort_when_field_direction_or_fragment_is_invalid() {
    for (String sort :
        new String[] {
          "",
          " ",
          "unknown",
          "id DOWN",
          "id ASC extra",
          "id,customerName",
          "totalUnits; drop table orders--"
        }) {
      assertThatThrownBy(
              () ->
                  new OrderListingCriteria(OrderStatus.PAID, OrderReadScope.Global.INSTANCE, sort))
          .isInstanceOf(InvalidOrderSortException.class);
    }
  }

  @Test
  void should_normalize_sort_when_valid_field_has_outer_spaces() {
    assertThat(
            new OrderListingCriteria(
                    OrderStatus.PAID, OrderReadScope.Global.INSTANCE, "  totalUnits DESC  ")
                .sort())
        .isEqualTo("totalUnits DESC");
  }

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
