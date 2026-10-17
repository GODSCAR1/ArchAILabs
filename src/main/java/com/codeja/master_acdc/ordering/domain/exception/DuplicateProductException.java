package com.codeja.master_acdc.ordering.domain.exception;

import java.util.UUID;

/** Señala que el mismo producto aparece en dos líneas distintas. */
public class DuplicateProductException extends InvalidOrderException {

  public DuplicateProductException(UUID productId) {
    super("product repeated: " + productId);
  }
}
