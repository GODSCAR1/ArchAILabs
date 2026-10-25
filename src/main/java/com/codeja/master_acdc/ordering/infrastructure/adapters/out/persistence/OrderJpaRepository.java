package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

  Optional<OrderJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

  @Query(
      "select new"
          + " com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence.OrderSummaryRow(o.id,"
          + " c.name, l.productId, case when l.productId is null then 0 else 1 end,"
          + " coalesce(l.quantity, 0)) from OrderJpaEntity o left join CustomerJpaEntity c on c.id"
          + " = o.customerId left join o.lines l where o.status = :status")
  List<OrderSummaryRow> findByStatus(@Param("status") String status);

  @Modifying(flushAutomatically = true)
  @Query(
      "update OrderJpaEntity o set o.status = 'CANCELLED' "
          + "where o.id = :orderId and o.customerId = :customerId "
          + "and o.status in ('PENDING', 'PAID')")
  int cancelIfCancelable(@Param("orderId") UUID orderId, @Param("customerId") UUID customerId);
}
