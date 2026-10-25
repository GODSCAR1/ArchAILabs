package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import java.util.List;

public class OrderSummaryQueryAdapter implements OrderSummaryQuery {

  private final OrderJpaRepository orderJpaRepository;
  private final CustomerJpaRepository customerJpaRepository;

  public OrderSummaryQueryAdapter(
      OrderJpaRepository orderJpaRepository, CustomerJpaRepository customerJpaRepository) {
    this.orderJpaRepository = orderJpaRepository;
    this.customerJpaRepository = customerJpaRepository;
  }

  @Override
  public List<OrderSummary> findByStatus(OrderStatus status) {
    return orderJpaRepository.findByStatus(status.name()).stream().map(this::toSummary).toList();
  }

  private OrderSummary toSummary(OrderJpaEntity order) {
    String customerName =
        customerJpaRepository
            .findById(order.customerId())
            .map(CustomerJpaEntity::name)
            .orElse(null);
    int totalUnits = order.lines().stream().mapToInt(OrderLineJpaEmbeddable::quantity).sum();
    return new OrderSummary(order.id(), customerName, order.lines().size(), totalUnits);
  }
}
