package com.codeja.master_acdc.inventory.api;

import com.codeja.master_acdc.inventory.application.port.ReserveStockUseCase;
import com.codeja.master_acdc.inventory.domain.model.ReservationResult;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * API pública de reserva de inventario.
 *
 * <p>Envuelve la reserva en una transacción propia con propagación REQUIRED: se une a la del pedido
 * cuando existe y abre la suya cuando se invoca sola, de modo que la reserva es todo o nada.
 */
public class InventoryFacade {

  private final ReserveStockUseCase reserveStockUseCase;
  private final TransactionTemplate transactionTemplate;

  public InventoryFacade(
      ReserveStockUseCase reserveStockUseCase, TransactionTemplate transactionTemplate) {
    this.reserveStockUseCase = reserveStockUseCase;
    this.transactionTemplate = transactionTemplate;
  }

  public ReservationResult reserve(List<StockLine> lines) {
    return transactionTemplate.execute(
        status -> {
          ReservationResult result = reserveStockUseCase.reserve(lines);
          if (result != ReservationResult.RESERVED) {
            status.setRollbackOnly();
          }
          return result;
        });
  }
}
