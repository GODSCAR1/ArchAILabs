package com.codeja.master_acdc.catalog.api;

import java.util.List;
import java.util.UUID;

/** Lectura por lote de precios actuales del catálogo, sin cache. */
public interface CatalogPrices {
  List<ProductPrice> findByIds(List<UUID> ids);

  record ProductPrice(UUID id, String sku, long price) {}
}
