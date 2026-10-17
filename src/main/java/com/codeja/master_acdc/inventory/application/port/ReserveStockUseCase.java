package com.codeja.master_acdc.inventory.application.port;

import com.codeja.master_acdc.inventory.domain.model.ReservationResult;
import com.codeja.master_acdc.inventory.domain.model.StockLine;
import java.util.List;

/** Caso de uso: reservar stock de un conjunto de líneas (todo o nada). */
public interface ReserveStockUseCase {

  ReservationResult reserve(List<StockLine> lines);
}
