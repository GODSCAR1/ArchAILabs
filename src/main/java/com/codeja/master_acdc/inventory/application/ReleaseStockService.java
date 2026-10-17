package com.codeja.master_acdc.inventory.application;

import static java.util.Comparator.comparing;

import com.codeja.master_acdc.inventory.application.port.ReleaseStockUseCase;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import com.codeja.master_acdc.inventory.domain.port.StockRepository;
import java.util.List;

public class ReleaseStockService implements ReleaseStockUseCase {

  private final StockRepository stockRepository;

  public ReleaseStockService(StockRepository stockRepository) {
    this.stockRepository = stockRepository;
  }

  @Override
  public void release(List<StockLine> lines) {
    // Procesar en orden de producto evita interbloqueos con reservas concurrentes.
    List<StockLine> ordered = lines.stream().sorted(comparing(StockLine::productId)).toList();
    for (StockLine line : ordered) {
      stockRepository.release(line.productId(), line.quantity());
    }
  }
}
