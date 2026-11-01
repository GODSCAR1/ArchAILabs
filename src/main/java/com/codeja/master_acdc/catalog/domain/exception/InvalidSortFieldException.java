package com.codeja.master_acdc.catalog.domain.exception;

public class InvalidSortFieldException extends RuntimeException {

  public InvalidSortFieldException(String field) {
    super("sort field is not allowed: " + field);
  }
}
