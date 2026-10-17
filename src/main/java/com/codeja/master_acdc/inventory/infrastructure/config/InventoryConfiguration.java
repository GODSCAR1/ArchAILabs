package com.codeja.master_acdc.inventory.infrastructure.config;

import com.codeja.master_acdc.inventory.api.InventoryFacade;
import com.codeja.master_acdc.inventory.application.ReleaseStockService;
import com.codeja.master_acdc.inventory.application.ReserveStockService;
import com.codeja.master_acdc.inventory.application.port.ReleaseStockUseCase;
import com.codeja.master_acdc.inventory.application.port.ReserveStockUseCase;
import com.codeja.master_acdc.inventory.domain.port.StockRepository;
import com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence.StockJpaRepository;
import com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence.StockRepositoryAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class InventoryConfiguration {

  @Bean
  public StockRepository stockRepository(StockJpaRepository stockJpaRepository) {
    return new StockRepositoryAdapter(stockJpaRepository);
  }

  @Bean
  public ReserveStockUseCase reserveStockUseCase(StockRepository stockRepository) {
    return new ReserveStockService(stockRepository);
  }

  @Bean
  public ReleaseStockUseCase releaseStockUseCase(StockRepository stockRepository) {
    return new ReleaseStockService(stockRepository);
  }

  @Bean
  public InventoryFacade inventoryFacade(
      ReserveStockUseCase reserveStockUseCase,
      ReleaseStockUseCase releaseStockUseCase,
      PlatformTransactionManager transactionManager) {
    return new InventoryFacade(
        reserveStockUseCase, releaseStockUseCase, new TransactionTemplate(transactionManager));
  }
}
