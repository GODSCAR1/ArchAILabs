package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.OrderListingCriteria;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import jakarta.persistence.EntityManager;
import java.util.List;

public class OrderSummaryQueryAdapter implements OrderSummaryQuery {
  private final EntityManager entityManager;

  public OrderSummaryQueryAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<OrderSummary> findByStatus(OrderListingCriteria criteria) {
    if (criteria.scope() == OrderReadScope.None.INSTANCE) {
      return List.of();
    }
    String filter =
        criteria.scope() instanceof OrderReadScope.Own ? " AND o.customer_id = :customer" : "";
    // S13-01: siembra intencionada para el laboratorio; se corrige en el change de remediación.
    String sql =
        "SELECT o.id AS id, c.name AS customerName, count(l.product_id) AS lineCount, "
            + "coalesce(sum(l.quantity), 0) AS totalUnits FROM orders o "
            + "LEFT JOIN customers c ON c.id = o.customer_id "
            + "LEFT JOIN order_lines l ON l.order_id = o.id WHERE o.status = :status"
            + filter
            + " GROUP BY o.id, c.name ORDER BY "
            + criteria.sort()
            + ", id ASC";
    var query =
        entityManager.unwrap(org.hibernate.Session.class).createNativeQuery(sql, Object[].class);
    query.setParameter("status", criteria.status().name());
    if (criteria.scope() instanceof OrderReadScope.Own own) {
      query.setParameter("customer", own.customerId());
    }
    return query.getResultList().stream()
        .map(OrderSummaryPersistenceMapper::fromNativeRow)
        .toList();
  }
}
