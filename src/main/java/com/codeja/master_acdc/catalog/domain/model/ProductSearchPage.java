package com.codeja.master_acdc.catalog.domain.model;

import java.util.List;

public record ProductSearchPage(
    List<ProductSearchRow> content, int page, int size, long totalElements, int totalPages) {

  public ProductSearchPage {
    content = List.copyOf(content);
  }
}
