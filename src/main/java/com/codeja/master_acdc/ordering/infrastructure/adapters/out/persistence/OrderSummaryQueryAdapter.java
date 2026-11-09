package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.OrderListingCriteria;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import java.util.List;
import org.hibernate.query.criteria.JpaRoot;
import org.springframework.data.jpa.domain.Specification;

public class OrderSummaryQueryAdapter implements OrderSummaryQuery {
  private final EntityManager entityManager;

  public OrderSummaryQueryAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<OrderSummary> findByStatus(OrderListingCriteria criteria) {
    var builder = entityManager.getCriteriaBuilder();
    var query = builder.createQuery(OrderSummaryRow.class);
    var order = (JpaRoot<OrderJpaEntity>) query.from(OrderJpaEntity.class);
    var customer = order.join(CustomerJpaEntity.class, JoinType.LEFT);
    customer.on(
        builder.equal(customer.get(CustomerJpaEntity_.id), order.get(OrderJpaEntity_.customerId)));
    var lines = order.join(OrderJpaEntity_.lines, JoinType.LEFT);
    var filters =
        Specification.allOf(
            OrderSpecifications.hasStatus(criteria.status()),
            OrderSpecifications.visibleTo(criteria.scope()));
    query.select(
        builder.construct(
            OrderSummaryRow.class,
            order.get(OrderJpaEntity_.id),
            customer.get(CustomerJpaEntity_.name),
            builder.count(lines.get(OrderLineJpaEmbeddable_.productId)),
            builder.coalesce(builder.sumAsLong(lines.get(OrderLineJpaEmbeddable_.quantity)), 0L)));
    query.where(filters.toPredicate(order, query, builder));
    query.groupBy(order.get(OrderJpaEntity_.id), customer.get(CustomerJpaEntity_.name));
    return entityManager.createQuery(query).getResultList().stream()
        .map(OrderSummaryPersistenceMapper::toDomain)
        .toList();
  }
}
