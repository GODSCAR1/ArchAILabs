package com.codeja.master_acdc.inventory.application.port;

import com.codeja.master_acdc.inventory.domain.model.StockLine;
import java.util.List;

/** Caso de uso: liberar stock devuelto de un conjunto de líneas. */
public interface ReleaseStockUseCase {

  void release(List<StockLine> lines);
}
