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
    var id = order.get(OrderJpaEntity_.id);
    var name = customer.get(CustomerJpaEntity_.name);
    var count = builder.count(lines.get(OrderLineJpaEmbeddable_.productId));
    var units =
        builder.coalesce(builder.sumAsLong(lines.get(OrderLineJpaEmbeddable_.quantity)), 0L);
    var filters =
        Specification.allOf(
            OrderSpecifications.hasStatus(criteria.status()),
            OrderSpecifications.visibleTo(criteria.scope()));
    query.select(builder.construct(OrderSummaryRow.class, id, name, count, units));
    query.where(filters.toPredicate(order, query, builder));
    query.groupBy(id, name);
    String[] parts = criteria.sort().split("\\s+");
    var expression =
        switch (parts[0]) {
          case "id" -> id;
          case "customerName" -> name;
          case "lineCount" -> count;
          case "totalUnits" -> units;
          default -> throw new IllegalStateException("Ordenación no válida");
        };
    var primary =
        parts.length == 2 && parts[1].equals("DESC")
            ? builder.desc(expression)
            : builder.asc(expression);
    query.orderBy(parts[0].equals("id") ? List.of(primary) : List.of(primary, builder.asc(id)));
    return entityManager.createQuery(query).getResultList().stream()
        .map(OrderSummaryPersistenceMapper::toDomain)
        .toList();
  }
}
