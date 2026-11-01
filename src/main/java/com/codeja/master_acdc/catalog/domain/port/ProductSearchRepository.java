package com.codeja.master_acdc.catalog.domain.port;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;

public interface ProductSearchRepository {

  ProductSearchPage search(ProductSearchCriteria criteria);
}
