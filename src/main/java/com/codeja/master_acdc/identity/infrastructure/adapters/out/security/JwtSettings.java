package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import java.net.URI;

public record JwtSettings(String privateKey, String issuer, String audience) {
  public JwtSettings {
    if (privateKey == null
        || privateKey.isBlank()
        || audience == null
        || audience.isBlank()
        || issuer == null
        || !URI.create(issuer).isAbsolute()
        || issuer.contains("${")
        || audience.contains("${")) {
      throw new IllegalArgumentException("Missing or invalid JWT settings");
    }
  }
}
