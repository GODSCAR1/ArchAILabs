package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import com.codeja.master_acdc.identity.domain.model.RefreshSecret;
import com.codeja.master_acdc.identity.domain.port.RefreshSecretGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class SecureRefreshSecretGenerator implements RefreshSecretGenerator {
  private final SecureRandom random = new SecureRandom();

  @Override
  public RefreshSecret generate() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    try {
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(value.getBytes(StandardCharsets.US_ASCII)));
      return new RefreshSecret(value, hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
