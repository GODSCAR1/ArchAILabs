package com.codeja.master_acdc.catalog.api;

import java.util.List;
import java.util.UUID;

/** Proyecta desde inventario el stock actual de los productos indicados. */
public interface CatalogStockProjection {
  void synchronize(List<UUID> productIds);
}
