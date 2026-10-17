package com.codeja.master_acdc.inventory.domain.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Puerto de salida para consultar y descontar stock de forma condicional. */
public interface StockRepository {

  /** Devuelve, de los identificadores pedidos, los que tienen registro de stock. */
  Set<UUID> findExistingProductIds(Collection<UUID> productIds);

  /**
   * Descuenta {@code quantity} unidades solo si el stock disponible alcanza.
   *
   * @return {@code true} si la actualización afectó a una fila; {@code false} si no había
   *     suficiente
   */
  boolean discountIfAvailable(UUID productId, int quantity);
}
