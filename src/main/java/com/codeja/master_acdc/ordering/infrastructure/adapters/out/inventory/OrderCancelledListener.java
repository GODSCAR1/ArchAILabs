package com.codeja.master_acdc.ordering.infrastructure.adapters.out.inventory;

import com.codeja.master_acdc.inventory.api.InventoryFacade;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;
import java.util.List;
import org.springframework.context.event.EventListener;

/**
 * Escucha la cancelación de un pedido y libera su stock vía {@code inventory.api}.
 *
 * <p>{@code @EventListener} es síncrono por defecto: corre en el mismo hilo y transacción que la
 * cancelación, de modo que una liberación fallida revierte también el estado del pedido.
 */
public class OrderCancelledListener {

  private final InventoryFacade inventoryFacade;

  public OrderCancelledListener(InventoryFacade inventoryFacade) {
    this.inventoryFacade = inventoryFacade;
  }

  @EventListener
  public void on(OrderCancelled event) {
    List<StockLine> stockLines =
        event.lines().stream()
            .map(line -> new StockLine(line.productId(), line.quantity()))
            .toList();
    inventoryFacade.release(stockLines);
  }
}
