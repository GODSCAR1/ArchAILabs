package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.port.Passwords;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class PasswordEncoderAdapter implements Passwords {
  private final PasswordEncoder encoder;
  private final String dummyHash;

  public PasswordEncoderAdapter(PasswordEncoder encoder) {
    this.encoder = encoder;
    this.dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  @Override
  public String encode(String password) {
    if (password == null
        || password.getBytes(StandardCharsets.UTF_8).length > 72
        || password.indexOf('\0') >= 0) {
      throw new IdentityException("invalid-password");
    }
    return encoder.encode(password);
  }

  @Override
  public boolean matches(String password, String hash) {
    // Oversize login input must still perform one comparison, never bcrypt's silent truncation.
    boolean representable =
        password != null
            && password.getBytes(StandardCharsets.UTF_8).length <= 72
            && password.indexOf('\0') < 0;
    boolean match = encoder.matches(representable ? password : "invalid-login-input", hash);
    return representable && match;
  }

  @Override
  public boolean matchesDummy(String password) {
    return matches(password, dummyHash);
  }
}
