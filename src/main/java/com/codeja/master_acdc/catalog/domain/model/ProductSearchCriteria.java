package com.codeja.master_acdc.catalog.domain.model;

import com.codeja.master_acdc.catalog.domain.exception.InvalidPriceRangeException;
import com.codeja.master_acdc.catalog.domain.exception.InvalidSortFieldException;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ProductSearchCriteria(
    @Nullable String text,
    @Nullable UUID categoryId,
    @Nullable Long minPrice,
    @Nullable Long maxPrice,
    Set<ProductStatus> statuses,
    boolean inStock,
    int page,
    int size,
    ProductSearchOrder order) {

  public ProductSearchCriteria {
    if (page < 0 || size < 1 || order == null) {
      throw new IllegalArgumentException("invalid search pagination or order");
    }
    if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
      throw new InvalidPriceRangeException();
    }
    if (!Set.of("name", "price", "createdAt").contains(order.field())) {
      throw new InvalidSortFieldException(order.field());
    }
    statuses = Set.copyOf(statuses == null ? Set.of() : statuses);
  }
}
