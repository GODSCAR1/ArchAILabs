package com.codeja.master_acdc.catalog.domain.port;

import com.codeja.master_acdc.catalog.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.catalog.domain.model.Product;
import com.codeja.master_acdc.catalog.domain.model.Sku;

/** Puerto de salida para persistir productos. */
public interface ProductRepository {

  boolean existsBySku(Sku sku);

  /**
   * Persiste el producto.
   *
   * @throws DuplicateSkuException si el sku ya existe, incluso si la colisión se descubre al
   *     insertar (carrera entre la comprobación previa y el guardado).
   */
  void save(Product product);
}
