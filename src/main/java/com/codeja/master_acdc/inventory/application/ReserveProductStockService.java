package com.codeja.master_acdc.inventory.application;

import com.codeja.master_acdc.inventory.application.port.ReserveProductStockUseCase;
import com.codeja.master_acdc.inventory.domain.model.ProductReservation;
import com.codeja.master_acdc.inventory.domain.model.StockBalance;
import com.codeja.master_acdc.inventory.domain.port.StockReservationRepository;
import java.util.UUID;

public class ReserveProductStockService implements ReserveProductStockUseCase {

  private final StockReservationRepository stockRepository;

  public ReserveProductStockService(StockReservationRepository stockRepository) {
    this.stockRepository = stockRepository;
  }

  @Override
  public ProductReservation reserve(UUID productId, int units) {
    StockBalance current = stockRepository.findForUpdateById(productId);
    StockBalance remaining = current.reserve(units);
    stockRepository.save(remaining);
    return new ProductReservation(productId, units, remaining.available());
  }
}
