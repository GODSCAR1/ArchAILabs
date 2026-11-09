package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public class OrderRepositoryAdapter implements OrderRepository {

  private final OrderJpaRepository orderJpaRepository;

  public OrderRepositoryAdapter(OrderJpaRepository orderJpaRepository) {
    this.orderJpaRepository = orderJpaRepository;
  }

  @Override
  public void save(Order order) {
    orderJpaRepository.saveAndFlush(OrderPersistenceMapper.toEntity(order));
  }

  @Override
  public Optional<Order> findVisibleById(UUID orderId, OrderReadScope scope) {
    Specification<OrderJpaEntity> id =
        (root, query, builder) -> builder.equal(root.get(OrderJpaEntity_.id), orderId);
    return orderJpaRepository
        .findOne(Specification.allOf(id, OrderSpecifications.visibleTo(scope)))
        .map(OrderPersistenceMapper::toDomain);
  }

  @Override
  public Optional<Order> findByIdAndCustomerId(UUID orderId, UUID customerId) {
    return orderJpaRepository
        .findByIdAndCustomerId(orderId, customerId)
        .map(OrderPersistenceMapper::toDomain);
  }

  @Override
  public boolean cancelIfCancelable(UUID orderId, UUID customerId) {
    int updated = orderJpaRepository.cancelIfCancelable(orderId, customerId);
    orderJpaRepository.flush();
    return updated > 0;
  }

  @Override
  public boolean refundIfRefundable(UUID orderId) {
    return orderJpaRepository.refundIfRefundable(orderId) == 1;
  }
}
