package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.model.RequestedOrderLine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderWebMapperTest {
  @Test
  @DisplayName("Listado ordenado por unidades descendentes")
  void should_preserve_sort_when_mapping_listing_criteria() {
    assertThat(OrderWebMapper.listingCriteria("PAID", "totalUnits DESC").sort())
        .isEqualTo("totalUnits DESC");
  }

  @Test
  void should_require_exact_status_when_mapping_listing_criteria() {
    assertThat(OrderWebMapper.listingCriteria("PAID", "id ASC"))
        .isEqualTo(
            new com.codeja.master_acdc.ordering.domain.model.OrderListingCriteria(
                OrderStatus.PAID,
                com.codeja.master_acdc.ordering.domain.model.OrderReadScope.None.INSTANCE,
                "id ASC"));
    for (String value : new String[] {null, "", " "}) {
      assertThatThrownBy(() -> OrderWebMapper.listingCriteria(value, "id ASC"))
          .isInstanceOf(
              com.codeja.master_acdc.ordering.domain.exception.MissingOrderStatusException.class);
    }
    for (String value : new String[] {"paid", "OTHER"}) {
      assertThatThrownBy(() -> OrderWebMapper.listingCriteria(value, "id ASC"))
          .isInstanceOf(
              com.codeja.master_acdc.ordering.domain.exception.InvalidOrderStatusException.class);
    }
  }

  @Test
  void should_preserve_raw_lines_when_mapping_request_before_authorization() {
    UUID product = UUID.randomUUID();
    assertThat(
            OrderWebMapper.toLines(
                new CreateOrderRequest(List.of(new CreateOrderRequest.Line(product, 0)))))
        .containsExactly(new RequestedOrderLine(product, 0));
    assertThat(OrderWebMapper.toLines(null)).isEmpty();
    assertThat(OrderWebMapper.toLines(new CreateOrderRequest(null))).isEmpty();
    assertThat(OrderWebMapper.orderId("bad-id")).isNull();
    assertThat(OrderWebMapper.orderId(product.toString())).isEqualTo(product);
  }

  @Test
  void should_map_public_fields_when_mapping_order_and_summary() {
    var order =
        new Order(
            UUID.randomUUID(),
            UUID.randomUUID(),
            OrderStatus.PAID,
            List.of(new OrderLine(UUID.randomUUID(), 3)));
    assertThat(OrderWebMapper.response(order))
        .isEqualTo(
            new OrderResponse(
                order.id(),
                OrderStatus.PAID,
                List.of(new OrderResponse.Line(order.lines().getFirst().productId(), 3))));
    assertThat(OrderWebMapper.summary(new OrderSummary(order.id(), null, 1, 3)))
        .isEqualTo(new OrderSummaryResponse(order.id(), null, 1, 3));
  }
}
