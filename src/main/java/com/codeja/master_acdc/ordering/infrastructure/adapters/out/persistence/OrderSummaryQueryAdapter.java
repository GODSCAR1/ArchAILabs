package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    List<OrderJpaEntity> orders = orderJpaRepository.findByStatus(status.name());
    Map<UUID, CustomerJpaEntity> customers = findCustomers(orders);
    return orders.stream().map(order -> toSummary(order, customers)).toList();
  }

  private Map<UUID, CustomerJpaEntity> findCustomers(List<OrderJpaEntity> orders) {
    List<UUID> customerIds = orders.stream().map(OrderJpaEntity::customerId).distinct().toList();
    return customerJpaRepository.findAllById(customerIds).stream()
        .collect(Collectors.toMap(CustomerJpaEntity::id, Function.identity()));
  }

  private OrderSummary toSummary(OrderJpaEntity order, Map<UUID, CustomerJpaEntity> customers) {
    CustomerJpaEntity customer = customers.get(order.customerId());
    String customerName = customer == null ? null : customer.name();
    int totalUnits = order.lines().stream().mapToInt(OrderLineJpaEmbeddable::quantity).sum();
    return new OrderSummary(order.id(), customerName, order.lines().size(), totalUnits);
  }
}
