package com.codeja.master_acdc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.extension.Extension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

public final class GlobalIdentityTestExtension implements Extension {
  static {
    initialize();
  }

  private static synchronized void initialize() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var pair = generator.generateKeyPair();
      Path pem = Files.createTempFile("master-acdc-identity-", ".pem");
      pem.toFile().deleteOnExit();
      String encoded =
          Base64.getMimeEncoder(64, new byte[] {'\n'})
              .encodeToString(pair.getPrivate().getEncoded());
      Files.writeString(
          pem,
          "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n",
          StandardCharsets.US_ASCII);
      System.setProperty("shop.jwt.private-key", pem.toString());
      System.setProperty("shop.jwt.issuer", "https://api.master-acdc.dev");
      System.setProperty("shop.jwt.audience", "master-acdc-api");
      System.setProperty("shop.ops.user", "identity-test-ops");
      System.setProperty(
          "shop.ops.password-hash",
          PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("test-ops-password"));
    } catch (GeneralSecurityException | IOException exception) {
      throw new IllegalStateException("Cannot initialize identity test material", exception);
    }
  }
}
