package com.codeja.master_acdc.catalog.application.port;

import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;

public interface ProductSearchUseCase {

  ProductSearchPage search(ProductSearchCriteria criteria);
}
