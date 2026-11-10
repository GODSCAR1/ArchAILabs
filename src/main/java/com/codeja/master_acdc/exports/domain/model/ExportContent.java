package com.codeja.master_acdc.exports.domain.model;

import java.util.Arrays;
import java.util.Objects;

public record ExportContent(byte[] bytes) {
  public ExportContent {
    bytes = Objects.requireNonNull(bytes).clone();
  }

  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ExportContent content && Arrays.equals(bytes, content.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
