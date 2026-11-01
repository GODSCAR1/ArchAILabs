package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

public class InvalidSearchParameterException extends RuntimeException {

  private final String parameter;

  public InvalidSearchParameterException(String parameter) {
    this.parameter = parameter;
  }

  String parameter() {
    return parameter;
  }
}
