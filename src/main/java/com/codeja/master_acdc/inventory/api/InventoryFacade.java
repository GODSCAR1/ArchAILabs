package com.codeja.master_acdc.inventory.api;

import com.codeja.master_acdc.inventory.application.port.ReleaseStockUseCase;
import com.codeja.master_acdc.inventory.application.port.ReserveStockUseCase;
import com.codeja.master_acdc.inventory.domain.model.ReservationResult;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * API pública de reserva y liberación de inventario.
 *
 * <p>Envuelve cada operación en una transacción propia con propagación REQUIRED: se une a la del
 * pedido cuando existe y abre la suya cuando se invoca sola, de modo que cada operación es todo o
 * nada.
 */
public class InventoryFacade {

  private final ReserveStockUseCase reserveStockUseCase;
  private final ReleaseStockUseCase releaseStockUseCase;
  private final TransactionTemplate transactionTemplate;

  public InventoryFacade(
      ReserveStockUseCase reserveStockUseCase,
      ReleaseStockUseCase releaseStockUseCase,
      TransactionTemplate transactionTemplate) {
    this.reserveStockUseCase = reserveStockUseCase;
    this.releaseStockUseCase = releaseStockUseCase;
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

  public void release(List<StockLine> lines) {
    transactionTemplate.executeWithoutResult(status -> releaseStockUseCase.release(lines));
  }
}
