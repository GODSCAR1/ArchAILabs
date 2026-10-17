package com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.inventory.domain.port.StockRepository;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class StockRepositoryAdapter implements StockRepository {

  private final StockJpaRepository stockJpaRepository;

  public StockRepositoryAdapter(StockJpaRepository stockJpaRepository) {
    this.stockJpaRepository = stockJpaRepository;
  }

  @Override
  public Set<UUID> findExistingProductIds(Collection<UUID> productIds) {
    if (productIds.isEmpty()) {
      return Set.of();
    }
    return stockJpaRepository.findProductIdsIn(productIds);
  }

  @Override
  public boolean discountIfAvailable(UUID productId, int quantity) {
    return stockJpaRepository.discountIfAvailable(productId, quantity) > 0;
  }
}
