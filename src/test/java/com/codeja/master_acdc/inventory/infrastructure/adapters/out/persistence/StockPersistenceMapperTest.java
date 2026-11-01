package com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.inventory.domain.model.StockBalance;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockPersistenceMapperTest {

  private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void should_map_stock_balance_to_entity_when_balance_is_valid() {
    StockJpaEntity entity = StockPersistenceMapper.toEntity(new StockBalance(PRODUCT_ID, 7));

    assertThat(entity.productId()).isEqualTo(PRODUCT_ID);
    assertThat(entity.available()).isEqualTo(7);
  }

  @Test
  void should_map_entity_to_stock_balance_when_entity_exists() {
    StockBalance balance = StockPersistenceMapper.toDomain(new StockJpaEntity(PRODUCT_ID, 7));

    assertThat(balance).isEqualTo(new StockBalance(PRODUCT_ID, 7));
  }
}
