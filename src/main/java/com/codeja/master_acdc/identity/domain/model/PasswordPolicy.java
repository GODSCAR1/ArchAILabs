package com.codeja.master_acdc.identity.domain.model;

import com.codeja.master_acdc.identity.domain.port.CommonPasswords;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
  private final CommonPasswords commonPasswords;

  public PasswordPolicy(CommonPasswords commonPasswords) {
    this.commonPasswords = commonPasswords;
  }

  public void validate(String password) {
    if (password == null
        || password.codePointCount(0, password.length()) < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72
        || password.indexOf('\0') >= 0
        || commonPasswords.contains(password)) {
      throw new IdentityException("invalid-password");
    }
  }
}
