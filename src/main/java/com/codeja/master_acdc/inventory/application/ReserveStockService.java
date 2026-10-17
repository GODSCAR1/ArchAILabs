package com.codeja.master_acdc.inventory.application;

import static java.util.Comparator.comparing;

import com.codeja.master_acdc.inventory.application.port.ReserveStockUseCase;
import com.codeja.master_acdc.inventory.domain.model.ReservationResult;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import com.codeja.master_acdc.inventory.domain.port.StockRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ReserveStockService implements ReserveStockUseCase {

  private final StockRepository stockRepository;

  public ReserveStockService(StockRepository stockRepository) {
    this.stockRepository = stockRepository;
  }

  @Override
  public ReservationResult reserve(List<StockLine> lines) {
    // Procesar en orden de producto evita interbloqueos entre reservas concurrentes.
    List<StockLine> ordered = lines.stream().sorted(comparing(StockLine::productId)).toList();

    Set<UUID> existing =
        stockRepository.findExistingProductIds(ordered.stream().map(StockLine::productId).toList());
    for (StockLine line : ordered) {
      if (!existing.contains(line.productId())) {
        return ReservationResult.UNKNOWN_PRODUCT;
      }
    }

    for (StockLine line : ordered) {
      if (!stockRepository.discountIfAvailable(line.productId(), line.quantity())) {
        return ReservationResult.INSUFFICIENT_STOCK;
      }
    }
    return ReservationResult.RESERVED;
  }
}
