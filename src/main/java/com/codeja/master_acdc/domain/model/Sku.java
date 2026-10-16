package com.codeja.master_acdc.domain.model;

import com.codeja.master_acdc.domain.exception.InvalidProductException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Sku único: {@code trim} + mayúsculas, con formato {@code ^[A-Z0-9-]{3,64}$}. */
public record Sku(String value) {

  private static final Pattern FORMAT = Pattern.compile("^[A-Z0-9-]{3,64}$");

  public Sku {
    if (value == null) {
      throw new InvalidProductException("sku must not be null");
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!FORMAT.matcher(normalized).matches()) {
      throw new InvalidProductException("sku must match " + FORMAT.pattern());
    }
    value = normalized;
  }
}
