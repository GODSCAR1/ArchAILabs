package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPersistenceMapperTest {
  @Test
  void should_preserve_identity_status_and_lines_when_round_tripping_order() {
    var order =
        new Order(
            UUID.randomUUID(),
            UUID.randomUUID(),
            OrderStatus.CANCELLED,
            List.of(new OrderLine(UUID.randomUUID(), 3)));
    assertThat(OrderPersistenceMapper.toDomain(OrderPersistenceMapper.toEntity(order)))
        .isEqualTo(order);
  }
}
