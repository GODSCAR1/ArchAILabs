package com.codeja.master_acdc.ordering.domain.model;

import org.jspecify.annotations.Nullable;

public record CustomerName(String firstName, @Nullable String lastName) {
  public CustomerName {
    if (firstName.isEmpty()
        || firstName.startsWith(" ")
        || firstName.endsWith(" ")
        || (lastName != null
            && (lastName.isEmpty() || lastName.startsWith(" ") || lastName.endsWith(" ")))) {
      throw new IllegalArgumentException("Invalid customer name components");
    }
  }

  public static CustomerName split(String name) {
    String normalized = name.replaceAll("\\A +| +\\z", "").replaceAll(" +", " ");
    int separator = normalized.indexOf(' ');
    return separator < 0
        ? new CustomerName(normalized, null)
        : new CustomerName(normalized.substring(0, separator), normalized.substring(separator + 1));
  }
}
