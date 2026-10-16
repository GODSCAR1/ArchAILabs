package com.codeja.master_acdc.domain.exception;

/** Señala que los datos de un producto incumplen una regla de negocio (formato o rango). */
public class InvalidProductException extends RuntimeException {

  public InvalidProductException(String message) {
    super(message);
  }
}
