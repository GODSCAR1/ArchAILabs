package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;

public final class OrderPersistenceMapper {
  private OrderPersistenceMapper() {}

  public static OrderJpaEntity toEntity(Order order) {
    var entity = new OrderJpaEntity(order.id(), order.customerId(), order.status().name());
    order
        .lines()
        .forEach(
            line ->
                entity.lines().add(new OrderLineJpaEmbeddable(line.productId(), line.quantity())));
    return entity;
  }

  public static Order toDomain(OrderJpaEntity entity) {
    return new Order(
        entity.id(),
        entity.customerId(),
        OrderStatus.valueOf(entity.status()),
        entity.lines().stream()
            .map(line -> new OrderLine(line.productId(), line.quantity()))
            .toList());
  }
}
