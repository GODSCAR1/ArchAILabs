package com.codeja.master_acdc.identity.domain.port;

public interface Passwords {
  String encode(String password);

  boolean matches(String password, String hash);

  boolean matchesDummy(String password);
}
