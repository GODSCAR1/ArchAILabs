package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import com.codeja.master_acdc.identity.domain.port.PasswordResetSecrets;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class SecurePasswordResetSecretGenerator implements PasswordResetSecrets {
  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  @Override
  public String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
