package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

public final class PemKeyLoader {
  private PemKeyLoader() {}

  public static RSAKey load(String path) {
    try {
      String pem = Files.readString(Path.of(path)).strip();
      if (!pem.startsWith("-----BEGIN PRIVATE KEY-----")
          || !pem.endsWith("-----END PRIVATE KEY-----")) {
        throw new IllegalArgumentException("Expected PKCS8 private PEM");
      }
      String body =
          pem.substring(
                  "-----BEGIN PRIVATE KEY-----".length(),
                  pem.length() - "-----END PRIVATE KEY-----".length())
              .replaceAll("\\s", "");
      var key =
          KeyFactory.getInstance("RSA")
              .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
      if (!(key instanceof RSAPrivateCrtKey privateKey)
          || privateKey.getModulus().bitLength() != 2048) {
        throw new IllegalArgumentException("Expected RSA CRT 2048 key");
      }
      var publicKey =
          (RSAPublicKey)
              KeyFactory.getInstance("RSA")
                  .generatePublic(
                      new RSAPublicKeySpec(
                          privateKey.getModulus(), privateKey.getPublicExponent()));
      RSAKey rsa = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
      return new RSAKey.Builder(rsa).keyID(rsa.computeThumbprint().toString()).build();
    } catch (IOException | GeneralSecurityException | JOSEException exception) {
      throw new IllegalArgumentException("Cannot load external RSA private key", exception);
    }
  }
}
