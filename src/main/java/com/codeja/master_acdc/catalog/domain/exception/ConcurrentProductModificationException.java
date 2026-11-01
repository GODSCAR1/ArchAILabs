package com.codeja.master_acdc.catalog.domain.exception;

import java.util.UUID;

/** Señala que el producto cambió desde que el cliente leyó su versión. */
public class ConcurrentProductModificationException extends RuntimeException {

  public ConcurrentProductModificationException(UUID productId) {
    super("concurrent product modification: " + productId);
  }
}
