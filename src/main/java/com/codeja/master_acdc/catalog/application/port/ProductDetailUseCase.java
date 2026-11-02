package com.codeja.master_acdc.catalog.application.port;

import com.codeja.master_acdc.catalog.domain.model.ProductDetail;

public interface ProductDetailUseCase {

  ProductDetail findBySku(String sku);
}
