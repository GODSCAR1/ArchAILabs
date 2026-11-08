package com.codeja.master_acdc.identity.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

public record Registration(String email, String password, String name) {
  private static final Pattern EMAIL =
      Pattern.compile(
          "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");

  public Registration {
    email = normalizeEmail(email);
    if (email.length() > 254 || email.indexOf('@') > 64 || !EMAIL.matcher(email).matches()) {
      throw new IdentityException("invalid-registration");
    }
    name = name == null ? email.substring(0, email.indexOf('@')) : name;
    if (name.isBlank() || name.length() > 120) {
      throw new IdentityException("invalid-registration");
    }
  }

  public static String normalizeEmail(String email) {
    if (email == null) {
      throw new IdentityException("invalid-registration");
    }
    return email.toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return "Registration[redacted]";
  }
}
