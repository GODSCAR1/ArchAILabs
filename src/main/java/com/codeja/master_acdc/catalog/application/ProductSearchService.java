package com.codeja.master_acdc.catalog.application;

import com.codeja.master_acdc.catalog.application.port.ProductSearchUseCase;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchCriteria;
import com.codeja.master_acdc.catalog.domain.model.ProductSearchPage;
import com.codeja.master_acdc.catalog.domain.port.ProductSearchRepository;

public class ProductSearchService implements ProductSearchUseCase {

  private final ProductSearchRepository productSearchRepository;

  public ProductSearchService(ProductSearchRepository productSearchRepository) {
    this.productSearchRepository = productSearchRepository;
  }

  @Override
  public ProductSearchPage search(ProductSearchCriteria criteria) {
    return productSearchRepository.search(criteria);
  }
}
