package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.ReservationOutcome;
import java.util.List;

/** Puerto de salida para reservar el stock de las líneas de un pedido (todo o nada). */
public interface StockReserver {

  ReservationOutcome reserve(List<OrderLine> lines);
}
