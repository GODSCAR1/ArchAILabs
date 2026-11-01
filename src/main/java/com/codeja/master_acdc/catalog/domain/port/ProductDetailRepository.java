package com.codeja.master_acdc.catalog.domain.port;

import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.codeja.master_acdc.catalog.domain.model.ProductReviewPage;
import java.util.Optional;

public interface ProductDetailRepository {

  Optional<ProductDetail> findBySku(String sku);

  Optional<ProductReviewPage> findReviewsBySku(String sku, int page, int size);
}
