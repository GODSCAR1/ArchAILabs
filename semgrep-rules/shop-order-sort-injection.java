// Fixtures detectan cada patrón y aceptan su remediación
class OrderSortFixture {
  void vulnerable(OrderListingCriteria criteria, EntityManager entityManager) {
    String filter = " AND o.customer_id = :customer";
    String sql =
        "SELECT o.id AS id, c.name AS customerName, count(l.product_id) AS lineCount, "
            + "coalesce(sum(l.quantity), 0) AS totalUnits FROM orders o "
            + "LEFT JOIN customers c ON c.id = o.customer_id "
            + "LEFT JOIN order_lines l ON l.order_id = o.id WHERE o.status = :status"
            + filter
            + " GROUP BY o.id, c.name ORDER BY "
            + criteria.sort()
            + ", id ASC";
    // ruleid: shop-order-sort-injection
    entityManager.unwrap(org.hibernate.Session.class).createNativeQuery(sql, Object[].class);
  }

  void safe(EntityManager entityManager) {
    var builder = entityManager.getCriteriaBuilder();
    var query = builder.createQuery(OrderSummaryRow.class);
    var order = query.from(OrderJpaEntity.class);
    query.orderBy(builder.asc(order.get(OrderJpaEntity_.id)));
    // ok: shop-order-sort-injection
    entityManager.createQuery(query);
  }
}
