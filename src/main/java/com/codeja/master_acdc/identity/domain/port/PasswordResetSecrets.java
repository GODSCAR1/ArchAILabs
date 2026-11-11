package com.codeja.master_acdc.identity.domain.port;

public interface PasswordResetSecrets {
  String generate();

  String hash(String token);
}
