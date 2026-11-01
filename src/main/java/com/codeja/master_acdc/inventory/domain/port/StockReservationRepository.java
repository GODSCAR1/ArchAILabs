package com.codeja.master_acdc.inventory.domain.port;

import com.codeja.master_acdc.inventory.domain.model.StockBalance;
import java.util.UUID;

/** Puerto de salida para reservar stock mediante una lectura bloqueada. */
public interface StockReservationRepository {

  /** Obtiene el balance del producto con el bloqueo necesario para reservarlo. */
  StockBalance findForUpdateById(UUID productId);

  /** Persiste el balance resultante de una reserva directa. */
  void save(StockBalance balance);
}
