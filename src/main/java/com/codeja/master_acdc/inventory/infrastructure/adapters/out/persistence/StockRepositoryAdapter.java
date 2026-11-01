package com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.inventory.domain.exception.StockBusyException;
import com.codeja.master_acdc.inventory.domain.exception.StockRecordNotFoundException;
import com.codeja.master_acdc.inventory.domain.model.StockBalance;
import com.codeja.master_acdc.inventory.domain.port.StockRepository;
import com.codeja.master_acdc.inventory.domain.port.StockReservationRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;

public class StockRepositoryAdapter implements StockRepository, StockReservationRepository {

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
  public StockBalance findForUpdateById(UUID productId) {
    try {
      Optional<StockJpaEntity> entity = stockJpaRepository.findForUpdateById(productId);
      return entity
          .map(StockPersistenceMapper::toDomain)
          .orElseThrow(() -> new StockRecordNotFoundException(productId));
    } catch (PessimisticLockingFailureException exception) {
      throw new StockBusyException(productId, exception);
    }
  }

  @Override
  public void save(StockBalance balance) {
    stockJpaRepository.saveAndFlush(StockPersistenceMapper.toEntity(balance));
  }

  @Override
  public boolean discountIfAvailable(UUID productId, int quantity) {
    return stockJpaRepository.discountIfAvailable(productId, quantity) > 0;
  }

  @Override
  public void release(UUID productId, int quantity) {
    if (stockJpaRepository.release(productId, quantity) == 0) {
      throw new StockRecordNotFoundException(productId);
    }
  }
}
