package com.codeja.master_acdc.catalog.application.port;

import com.codeja.master_acdc.catalog.domain.model.Product;
import java.math.BigDecimal;
import java.util.UUID;

/** Caso de uso: editar nombre y precio de un producto. */
public interface EditProductUseCase {

  Product edit(UUID productId, String name, BigDecimal price, Long version);
}
