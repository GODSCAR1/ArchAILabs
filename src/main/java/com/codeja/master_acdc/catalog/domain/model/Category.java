package com.codeja.master_acdc.catalog.domain.model;

import com.codeja.master_acdc.catalog.domain.exception.InvalidProductException;
import java.util.UUID;

public record Category(UUID id, String name) {

  public static final UUID UNCATEGORIZED_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  public Category {
    if (id == null || name == null || name.isBlank()) {
      throw new InvalidProductException("category must have an id and name");
    }
  }
}
