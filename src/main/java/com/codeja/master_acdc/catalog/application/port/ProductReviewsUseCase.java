package com.codeja.master_acdc.catalog.application.port;

import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;

public interface ProductReviewsUseCase {

  ProductReviewPage findBySku(String sku, int page, int size);
}
