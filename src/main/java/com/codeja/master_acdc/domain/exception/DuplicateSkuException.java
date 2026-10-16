package com.codeja.master_acdc.domain.exception;

/** Señala que el sku ya está en uso, incluso si la colisión se descubre al insertar (carrera). */
public class DuplicateSkuException extends RuntimeException {

  public DuplicateSkuException(String sku) {
    super("sku already exists: " + sku);
  }

  public DuplicateSkuException(String sku, Throwable cause) {
    super("sku already exists: " + sku, cause);
  }
}
