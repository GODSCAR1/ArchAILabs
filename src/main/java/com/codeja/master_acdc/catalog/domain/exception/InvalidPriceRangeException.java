package com.codeja.master_acdc.catalog.domain.exception;

public class InvalidPriceRangeException extends RuntimeException {

  public InvalidPriceRangeException() {
    super("minPrice must not be greater than maxPrice");
  }
}
