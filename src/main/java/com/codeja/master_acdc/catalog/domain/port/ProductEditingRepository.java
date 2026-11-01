package com.codeja.master_acdc.catalog.domain.port;

import com.codeja.master_acdc.catalog.domain.model.Product;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para localizar y guardar ediciones de productos. */
public interface ProductEditingRepository {

  Optional<Product> findById(UUID productId);

  Product save(Product product);
}
