package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.Optional;
import java.util.UUID;

public class OrderRepositoryAdapter implements OrderRepository {

  private final OrderJpaRepository orderJpaRepository;

  public OrderRepositoryAdapter(OrderJpaRepository orderJpaRepository) {
    this.orderJpaRepository = orderJpaRepository;
  }

  @Override
  public void save(Order order) {
    orderJpaRepository.saveAndFlush(OrderJpaEntity.from(order));
  }

  @Override
  public Optional<Order> findByIdAndCustomerId(UUID orderId, UUID customerId) {
    return orderJpaRepository
        .findByIdAndCustomerId(orderId, customerId)
        .map(OrderJpaEntity::toDomain);
  }

  @Override
  public boolean cancelIfCancelable(UUID orderId, UUID customerId) {
    int updated = orderJpaRepository.cancelIfCancelable(orderId, customerId);
    orderJpaRepository.flush();
    return updated > 0;
  }
}
