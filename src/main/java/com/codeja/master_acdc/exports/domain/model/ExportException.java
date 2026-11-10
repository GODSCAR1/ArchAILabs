package com.codeja.master_acdc.exports.domain.model;

public final class ExportException extends RuntimeException {
  private final String code;

  public ExportException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
