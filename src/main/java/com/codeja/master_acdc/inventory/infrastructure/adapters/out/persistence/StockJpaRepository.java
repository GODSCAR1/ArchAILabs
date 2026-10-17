package com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockJpaRepository extends JpaRepository<StockJpaEntity, UUID> {

  @Query("select stock.productId from StockJpaEntity stock where stock.productId in :productIds")
  Set<UUID> findProductIdsIn(@Param("productIds") Collection<UUID> productIds);

  @Modifying
  @Query(
      "update StockJpaEntity stock set stock.available = stock.available - :quantity "
          + "where stock.productId = :productId and stock.available >= :quantity")
  int discountIfAvailable(@Param("productId") UUID productId, @Param("quantity") int quantity);

  @Modifying(flushAutomatically = true)
  @Query(
      "update StockJpaEntity stock set stock.available = stock.available + :quantity "
          + "where stock.productId = :productId")
  int release(@Param("productId") UUID productId, @Param("quantity") int quantity);
}
