package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrderSummaryQueryAdapter implements OrderSummaryQuery {

  private final OrderJpaRepository orderJpaRepository;

  public OrderSummaryQueryAdapter(OrderJpaRepository orderJpaRepository) {
    this.orderJpaRepository = orderJpaRepository;
  }

  @Override
  public List<OrderSummary> findByStatus(OrderStatus status) {
    Map<UUID, List<OrderSummaryRow>> rowsByOrder =
        orderJpaRepository.findByStatus(status.name()).stream()
            .collect(
                Collectors.groupingBy(
                    OrderSummaryRow::id, LinkedHashMap::new, Collectors.toList()));
    return rowsByOrder.values().stream().map(this::toSummary).toList();
  }

  private OrderSummary toSummary(List<OrderSummaryRow> rows) {
    OrderSummaryRow first = rows.getFirst();
    int lineCount = rows.stream().mapToInt(OrderSummaryRow::lineCount).sum();
    int totalUnits = rows.stream().mapToInt(OrderSummaryRow::totalUnits).sum();
    List<UUID> productIds =
        rows.stream().map(OrderSummaryRow::productId).filter(id -> id != null).toList();
    return new OrderSummary(first.id(), first.customerName(), lineCount, totalUnits, productIds);
  }
}
