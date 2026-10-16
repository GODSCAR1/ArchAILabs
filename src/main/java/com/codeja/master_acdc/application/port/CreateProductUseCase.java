package com.codeja.master_acdc.application.port;

import com.codeja.master_acdc.domain.model.Product;
import java.math.BigDecimal;

/** Caso de uso: crear un producto. */
public interface CreateProductUseCase {

  Product create(String name, String sku, BigDecimal price);
}
