package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import java.util.List;

public class OrderSummaryQueryAdapter implements OrderSummaryQuery {

  private final OrderJpaRepository orderJpaRepository;

  public OrderSummaryQueryAdapter(OrderJpaRepository orderJpaRepository) {
    this.orderJpaRepository = orderJpaRepository;
  }

  @Override
  public List<OrderSummary> findByStatus(OrderStatus status) {
    return orderJpaRepository.findByStatus(status.name()).stream()
        .map(
            row ->
                new OrderSummary(
                    row.id(),
                    row.customerName(),
                    Math.toIntExact(row.lineCount()),
                    Math.toIntExact(row.totalUnits())))
        .toList();
  }
}
