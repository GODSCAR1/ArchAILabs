package com.codeja.master_acdc.domain.model;

import com.codeja.master_acdc.domain.exception.InvalidProductException;

/** Nombre de producto: 1–120 caracteres tras {@code trim}. */
public record ProductName(String value) {

  private static final int MAX_LENGTH = 120;

  public ProductName {
    if (value == null) {
      throw new InvalidProductException("name must not be null");
    }
    String normalized = value.trim();
    if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) {
      throw new InvalidProductException(
          "name must have between 1 and " + MAX_LENGTH + " characters");
    }
    value = normalized;
  }
}
