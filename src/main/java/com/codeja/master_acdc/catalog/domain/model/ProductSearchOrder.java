package com.codeja.master_acdc.catalog.domain.model;

public record ProductSearchOrder(String field, Direction direction) {

  public ProductSearchOrder {
    if (field == null || direction == null) {
      throw new IllegalArgumentException("sort field and direction are required");
    }
  }

  public enum Direction {
    ASC,
    DESC
  }
}
