package com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.inventory.domain.model.StockBalance;

final class StockPersistenceMapper {

  private StockPersistenceMapper() {}

  static StockBalance toDomain(StockJpaEntity entity) {
    return new StockBalance(entity.productId(), entity.available());
  }

  static StockJpaEntity toEntity(StockBalance balance) {
    return new StockJpaEntity(balance.productId(), balance.available());
  }
}
