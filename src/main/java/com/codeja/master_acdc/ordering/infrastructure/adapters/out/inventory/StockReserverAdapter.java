package com.codeja.master_acdc.ordering.infrastructure.adapters.out.inventory;

import com.codeja.master_acdc.inventory.api.InventoryFacade;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.ReservationOutcome;
import com.codeja.master_acdc.ordering.domain.port.StockReserver;
import java.util.List;

/** Adapta la reserva de stock de {@code inventory.api} al puerto del dominio de pedidos. */
public class StockReserverAdapter implements StockReserver {

  private final InventoryFacade inventoryFacade;

  public StockReserverAdapter(InventoryFacade inventoryFacade) {
    this.inventoryFacade = inventoryFacade;
  }

  @Override
  public ReservationOutcome reserve(List<OrderLine> lines) {
    List<StockLine> stockLines =
        lines.stream().map(line -> new StockLine(line.productId(), line.quantity())).toList();
    return switch (inventoryFacade.reserve(stockLines)) {
      case RESERVED -> ReservationOutcome.RESERVED;
      case INSUFFICIENT_STOCK -> ReservationOutcome.INSUFFICIENT_STOCK;
      case UNKNOWN_PRODUCT -> ReservationOutcome.UNKNOWN_PRODUCT;
    };
  }
}
